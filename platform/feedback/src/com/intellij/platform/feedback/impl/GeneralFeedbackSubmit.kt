// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.impl

import com.intellij.ide.RegionUrlMapper
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.feedback.impl.statistics.FeedbackSendActionCountCollector
import com.intellij.util.PlatformUtils
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.HttpRequests.JSON_CONTENT_TYPE
import kotlinx.serialization.json.*
import java.io.IOException
import java.net.HttpURLConnection
import javax.net.ssl.HttpsURLConnection

private const val TEST_FEEDBACK_URL = "https://forms-stgn.w3jbcom-nonprod.aws.intellij.net/feedback"
private const val PRODUCTION_FEEDBACK_URL = "https://forms-service.jetbrains.com/feedback"

private const val FEEDBACK_FORM_ID_ONLY_DATA = "feedback/ide"
private const val FEEDBACK_FORM_ID_WITH_DETAILED_ANSWER = "v2/feedback/ide_with_detailed_answer"

/** Should be used inside JSON top-level keys to distinguish reports without any external information */
const val FEEDBACK_REPORT_ID_KEY: String = "feedback_id"

private const val FEEDBACK_FROM_ID_KEY = "formid"
private const val FEEDBACK_AUTO_SOLVE_TICKET_KEY = "autosolve"
private const val FEEDBACK_INTELLIJ_PRODUCT_KEY = "intellij_product"
private const val FEEDBACK_TYPE_KEY = "feedback_type"
private const val FEEDBACK_PRIVACY_CONSENT_KEY = "privacy_consent"
private const val FEEDBACK_PRIVACY_CONSENT_TYPE_KEY = "privacy_consent_type"
private const val FEEDBACK_COLLECTED_DATA_KEY = "collected_data"
private const val FEEDBACK_EMAIL_KEY = "email"
private const val FEEDBACK_SUBJECT_KEY = "subject"
private const val FEEDBACK_COMMENT_KEY = "comment"
private const val FEEDBACK_TAGS_KEY = "tags"

const val DEFAULT_FEEDBACK_CONSENT_ID = "rsch.statistics.feedback.common"

private const val REQUEST_ID_KEY = "Request-Id"

private const val EMAIL_PLACEHOLDER = "<EMAIL>"
internal val EMAIL_REGEX = Regex("^[a-zA-Z0-9\\\\._%+!$&*=^|~#{}-]+@([a-zA-Z0-9\\\\-]+\\.)+([a-zA-Z]{2,22})$")
internal val SPACE_SYMBOL_REGEX = Regex("\\s")

private val LOG = Logger.getInstance(FeedbackRequestDataHolder::class.java)

sealed interface FeedbackRequestDataHolder {
  val feedbackType: String
  val collectedData: JsonObject

  fun toJsonObject(): JsonObject
}

/**
 * Feedback request data for answers that do not include a detailed answer and the user's email.
 * Feedback request data will be sent to WebTeam Backend and stored on the AWS S3.
 * The 'collectedData' parameter MUST not contain any user email or other personal information.
 */
data class FeedbackRequestData(override val feedbackType: String,
                               override val collectedData: JsonObject) : FeedbackRequestDataHolder {
  override fun toJsonObject(): JsonObject {
    return buildJsonObject {
      put(FEEDBACK_FROM_ID_KEY, FEEDBACK_FORM_ID_ONLY_DATA)
      put(FEEDBACK_INTELLIJ_PRODUCT_KEY, getProductTag())
      put(FEEDBACK_TYPE_KEY, feedbackType)
      put(FEEDBACK_COLLECTED_DATA_KEY, cleanFeedbackFromEmails(collectedData))
    }
  }
}

/**
 * Feedback request data for answers that include a detailed answer and the user's email.
 *
 * The 'collectedData' parameter MUST not contain any user email or other personal information.
 * Feedback request data will be sent to WebTeam Backend and stored on the AWS S3 in any case.
 * Also a ticket with feedback data will be submitted to Zendesk if email is not empty.
 * The created ticket can be auto solved in Zendesk by specifying 'autoSolveTicket' parameter as true.
 */
data class FeedbackRequestDataWithDetailedAnswer(val email: String,
                                                 val title: String,
                                                 val description: String,
                                                 val privacyConsentType: String,
                                                 val autoSolveTicket: Boolean,
                                                 val ticketTags: List<String>,
                                                 override val feedbackType: String,
                                                 override val collectedData: JsonObject) : FeedbackRequestDataHolder {
  override fun toJsonObject(): JsonObject {
    return buildJsonObject {
      put(FEEDBACK_FROM_ID_KEY, FEEDBACK_FORM_ID_WITH_DETAILED_ANSWER)
      put(FEEDBACK_AUTO_SOLVE_TICKET_KEY, autoSolveTicket)
      put(FEEDBACK_EMAIL_KEY, if (email.matches(EMAIL_REGEX)) email else "")
      put(FEEDBACK_SUBJECT_KEY, title)
      put(FEEDBACK_COMMENT_KEY, description)
      put(FEEDBACK_INTELLIJ_PRODUCT_KEY, getProductTag())
      put(FEEDBACK_TYPE_KEY, feedbackType)
      put(FEEDBACK_PRIVACY_CONSENT_KEY, true)
      put(FEEDBACK_PRIVACY_CONSENT_TYPE_KEY, privacyConsentType)
      put(FEEDBACK_TAGS_KEY, buildJsonArray { ticketTags.forEach { add(it.replace(SPACE_SYMBOL_REGEX, "_")) } })
      put(FEEDBACK_COLLECTED_DATA_KEY, cleanFeedbackFromEmails(collectedData))
    }
  }
}

fun submitFeedback(feedbackData: FeedbackRequestDataHolder,
                   onDone: () -> Unit,
                   onError: () -> Unit,
                   feedbackRequestType: FeedbackRequestType = FeedbackRequestType.TEST_REQUEST) {
  ApplicationManager.getApplication().executeOnPooledThread {
    val feedbackUrl = when (feedbackRequestType) {
      FeedbackRequestType.NO_REQUEST -> return@executeOnPooledThread
      FeedbackRequestType.TEST_REQUEST -> TEST_FEEDBACK_URL
      FeedbackRequestType.PRODUCTION_REQUEST -> PRODUCTION_FEEDBACK_URL
    }

    val regionalFeedbackUrl = RegionUrlMapper.mapUrl(feedbackUrl) ?: feedbackUrl
    LOG.info("Feedback sent to $regionalFeedbackUrl")
    sendFeedback(regionalFeedbackUrl, feedbackData, onDone, onError)
  }
}

private fun sendFeedback(feedbackUrl: String,
                         feedbackData: FeedbackRequestDataHolder,
                         onDone: () -> Unit,
                         onError: () -> Unit) {
  val requestData = feedbackData.toJsonObject().toString()

  try {
    HttpRequests
      .post(feedbackUrl, JSON_CONTENT_TYPE)
      .productNameAsUserAgent()
      .accept("application/json")
      .connect {
        try {
          it.write(requestData)
          val connection = it.connection

          if (connection is HttpsURLConnection && connection.responseCode != 200) {
            val requestId = it.connection.getHeaderField(REQUEST_ID_KEY)
            val errorResponse = it.readError()
            LOG.info("Failed to submit feedback. Feedback data:\n$requestData\nStatus code:${connection.responseCode}\n" +
                     "Server response:${errorResponse}\nRequest ID:${requestId}")
            onError()
            FeedbackSendActionCountCollector.logFeedbackSendFail()
            return@connect
          }

          val bytes = it.inputStream.readAllBytes()
          LOG.info(bytes.toString(Charsets.UTF_8))
          val requestId = it.connection.getHeaderField(REQUEST_ID_KEY)
          LOG.info("Feedback submitted successfully. Record ID is ${requestId}")
        }
        catch (e: IOException) {
          val errorResponse = (it.connection as HttpURLConnection).errorStream?.readAllBytes()?.toString(Charsets.UTF_8)
          LOG.info("Failed to submit feedback. Feedback data:\n$requestData\n" +
                   "Server response:\n$errorResponse\n" +
                   "Exception: ${e.stackTraceToString()}")
          onError()
          FeedbackSendActionCountCollector.logFeedbackSendFail()
          return@connect
        }
        if (feedbackData is FeedbackRequestData) {
          FeedbackSendActionCountCollector.logFeedbackSendSuccess()
        }
        onDone()
      }
  }
  catch (e: IOException) {
    LOG.info("Failed to submit feedback. Feedback data:\n$requestData\nError message:\n${e.message}")
    onError()
    FeedbackSendActionCountCollector.logFeedbackSendFail()
    return
  }
}

enum class FeedbackRequestType {
  NO_REQUEST, // can be used during feedback UI/statistics development and debug
  TEST_REQUEST,
  PRODUCTION_REQUEST
}

fun cleanFeedbackFromEmails(jsonElement: JsonElement): JsonElement {
  return when (jsonElement) {
    is JsonObject -> {
      buildJsonObject {
        jsonElement.forEach { (key, element) ->
          put(key, cleanFeedbackFromEmails(element))
        }
      }
    }
    is JsonArray -> {
      buildJsonArray {
        jsonElement.forEach { element ->
          add(cleanFeedbackFromEmails(element))
        }
      }
    }
    is JsonPrimitive -> {
      if (EMAIL_REGEX.find(jsonElement.content) != null) {
        JsonPrimitive(EMAIL_REGEX.replace(jsonElement.content, EMAIL_PLACEHOLDER))
      }
      else {
        jsonElement
      }
    }
  }
}

/**
 * @return product tag.
 * @see <a href="https://youtrack.jetbrains.com/issue/ZEN-1460#focus=Comments-27-5692479.0-0">ZEN-1460</a> for more information
 */
internal fun getProductTag(): String {
  return when {
    PlatformUtils.isIntelliJ() -> "ij_idea1"
    PlatformUtils.isPhpStorm() -> "ij_phpstorm1"
    PlatformUtils.isWebStorm() -> "ij_webstorm1"
    PlatformUtils.isPyCharm() -> "ij_pycharm1"
    PlatformUtils.isRubyMine() -> "ij_rubymine1"
    PlatformUtils.isAppCode() -> "ij_appcode1"
    PlatformUtils.isCLion() && !isCLionNova() -> "ij_clion1"
    PlatformUtils.isCLion() && isCLionNova() -> "ij_clion_nova1"
    PlatformUtils.isDataGrip() -> "ij_datagrip1"
    PlatformUtils.isGoIde() -> "ij_goland1"
    PlatformUtils.isJetBrainsClient() -> "ij_code_with_me1"
    PlatformUtils.isDataSpell() -> "ij_dataspell1"
    PlatformUtils.isRider() -> "ij_rider1"
    PlatformUtils.isRustRover() -> "ij_rustrover1"
    PlatformUtils.isAqua() -> "ij_aqua1"
    else -> "undefined"
  }
}

private fun isCLionNova(): Boolean {
  return ApplicationNamesInfo.getInstance().fullProductNameWithEdition.contains("Nova")
}