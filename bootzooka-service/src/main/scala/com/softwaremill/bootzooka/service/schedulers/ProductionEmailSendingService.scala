package com.softwaremill.bootzooka.service.schedulers

import com.softwaremill.common.sqs.email.EmailSender
import com.softwaremill.bootzooka.service.config.BootzookaConfiguration._
import com.softwaremill.common.sqs.util.EmailDescription
import javax.mail.MessagingException
import com.softwaremill.common.sqs.{ ReceivedMessage, Queue, SQS }
import com.google.common.base.Optional
import scala.util.control.Breaks._
import com.softwaremill.bootzooka.service.templates.EmailContentWithSubject

class ProductionEmailSendingService extends EmailSendingService {

  val sqsClient = new SQS("queue.amazonaws.com", awsAccessKeyId, awsSecretAccessKey)
  val emailQueue: Queue = sqsClient.getQueueByName(taskSQSQueue)
  emailQueue.setReceiveMessageWaitTime(20)

  def run() {
    logger.debug("Checking emails waiting in the Amazon SQS")
    var messageOpt: Optional[ReceivedMessage] = emailQueue.receiveSingleMessage

    breakable {
      while (messageOpt.isPresent) {
        val message: ReceivedMessage = messageOpt.get()
        val emailToSend: EmailDescription = message.getMessage.asInstanceOf[EmailDescription]

        try {
          EmailSender.send(smtpHost, smtpPort, smtpUserName, smtpPassword, from, encoding, emailToSend)
          logger.info("Email sent!")
          emailQueue.deleteMessage(message)
        } catch {
          case e: MessagingException =>
            logger.error(s"Sending email failed: ${e.getMessage}")
            break()
        }

        messageOpt = emailQueue.receiveSingleMessage
      }
    }
  }

  def scheduleEmail(address: String, emailData: EmailContentWithSubject) {
    emailQueue.sendSerializable(new EmailDescription(address, emailData.content, emailData.subject))
  }
}
