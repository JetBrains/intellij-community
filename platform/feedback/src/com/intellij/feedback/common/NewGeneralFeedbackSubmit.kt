// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback

import com.intellij.feedback.common.FeedbackRequestType
import com.intellij.feedback.common.getProductTag
import com.intellij.feedback.common.notification.ThanksForFeedbackNotification
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection

private const val TEST_FEEDBACK_URL = "https://forms-stgn.w3jbcom-nonprod.aws.intellij.net/feedback"
private const val PRODUCTION_FEEDBACK_URL = "https://forms-service.jetbrains.com/feedback"

private const val FEEDBACK_FORM_ID_ONLY_DATA = "feedback/ide"
private const val FEEDBACK_FORM_ID_WITH_DETAILED_ANSWER = "feedback/ide_with_detailed_answer"

private const val FEEDBACK_FROM_ID_KEY = "formid"
private const val FEEDBACK_INTELLIJ_PRODUCT_KEY = "intellij_product"
private const val FEEDBACK_TYPE_KEY = "feedback_type"
private const val FEEDBACK_COLLECTED_DATA_KEY = "collected_data"
private const val FEEDBACK_EMAIL_KEY = "email"
private const val FEEDBACK_SUBJECT_KEY = "subject"
private const val FEEDBACK_COMMENT_KEY = "comment"

private val LOG = Logger.getInstance(FeedbackRequestDataHolder::class.java)

sealed interface FeedbackRequestDataHolder {
  val feedbackType: String
  val collectedData: String

  fun toJsonObject(): JsonObject
}

/**
 * Feedback request data for answers that do not include a detailed answer and the user's email.
 * Sent to WebTeam Backend and stored only on AWS S3.
 */
data class FeedbackRequestData(override val feedbackType: String,
                               override val collectedData: String) : FeedbackRequestDataHolder {
  override fun toJsonObject(): JsonObject {
    return buildJsonObject {
      put(FEEDBACK_FROM_ID_KEY, FEEDBACK_FORM_ID_ONLY_DATA)
      put(FEEDBACK_INTELLIJ_PRODUCT_KEY, getProductTag())
      put(FEEDBACK_TYPE_KEY, feedbackType)
      put(FEEDBACK_COLLECTED_DATA_KEY, collectedData)
    }
  }
}

/**
 * Feedback request data for answers that include a detailed answer and the user's email.
 * Sent to WebTeam Backend. Stored on the AWS S3 and also submit ticket to Zendesk.
 * The created ticket will not be closed immediately, and it is assumed that a support specialist will look at it.
 */
data class FeedbackRequestDataWithDetailedAnswer(val email: String,
                                                 val title: String,
                                                 val description: String,
                                                 override val feedbackType: String,
                                                 override val collectedData: String) : FeedbackRequestDataHolder {
  override fun toJsonObject(): JsonObject {
    return buildJsonObject {
      put(FEEDBACK_FROM_ID_KEY, FEEDBACK_FORM_ID_WITH_DETAILED_ANSWER)
      put(FEEDBACK_EMAIL_KEY, email)
      put(FEEDBACK_SUBJECT_KEY, title)
      put(FEEDBACK_COMMENT_KEY, description)
      put(FEEDBACK_INTELLIJ_PRODUCT_KEY, getProductTag())
      put(FEEDBACK_TYPE_KEY, feedbackType)
      put(FEEDBACK_COLLECTED_DATA_KEY, collectedData)
    }
  }
}


fun submitFeedback(project: Project?,
                   feedbackData: FeedbackRequestDataHolder,
                   onDone: () -> Unit,
                   onError: () -> Unit,
                   feedbackRequestType: FeedbackRequestType = FeedbackRequestType.TEST_REQUEST,
                   showNotification: Boolean = true) {
  ApplicationManager.getApplication().executeOnPooledThread {
    val feedbackUrl = when (feedbackRequestType) {
      FeedbackRequestType.NO_REQUEST -> return@executeOnPooledThread
      FeedbackRequestType.TEST_REQUEST -> TEST_FEEDBACK_URL
      FeedbackRequestType.PRODUCTION_REQUEST -> PRODUCTION_FEEDBACK_URL
    }
    sendFeedback(feedbackUrl, feedbackData, onDone, onError)
  }


  if (showNotification) {
    ApplicationManager.getApplication().invokeLater {
      ThanksForFeedbackNotification().notify(project)
    }
  }
}

private fun sendFeedback(feedbackUrl: String,
                         feedbackData: FeedbackRequestDataHolder,
                         onDone: () -> Unit,
                         onError: () -> Unit) {
  val requestData = feedbackData.toJsonObject().toString()

  try {
    HttpRequests
      .post(feedbackUrl, "application/json")
      .productNameAsUserAgent()
      .accept("application/json")
      .connect {
        try {
          it.write(requestData)
          val bytes = it.inputStream.readAllBytes()
          LOG.info(bytes.toString(Charsets.UTF_8))
        }
        catch (e: IOException) {
          val errorResponse = (it.connection as HttpURLConnection).errorStream.readAllBytes().toString(Charsets.UTF_8)
          LOG.info("Failed to submit feedback. Feedback data:\n$requestData\nServer response:\n$errorResponse")
          onError()
          return@connect
        }
        onDone()
      }
  }
  catch (e: IOException) {
    LOG.info("Failed to submit feedback. Feedback data:\n$requestData\nError message:\n${e.message}")
    onError()
    return
  }
}