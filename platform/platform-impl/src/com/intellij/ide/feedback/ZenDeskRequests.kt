// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.feedback

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.ide.impl.customization.ZenDeskFeedbackFormData
import com.intellij.ui.LicensingFacade
import com.intellij.util.io.HttpRequests
import java.io.IOException
import java.net.HttpURLConnection

class ZenDeskRequests {
  private val objectMapper by lazy { ObjectMapper() }

  internal fun submit(zenDeskFormData: ZenDeskFeedbackFormData, email: String, subject: String, text: String, 
                      customFieldValues: CustomFieldValues, onDone: () -> Unit, onError: () -> Unit) {
    val fieldIds = zenDeskFormData.fieldIds
    val customFields = listOfNotNull(
      ZenDeskCustomField(fieldIds.product, zenDeskFormData.productId),
      ZenDeskCustomField(fieldIds.country, "sl_unknown"),
      customFieldValues.rating?.let { rating ->
        ZenDeskCustomField(fieldIds.rating, rating)
      },
      ZenDeskCustomField(fieldIds.build, ApplicationInfo.getInstance().build.asString()),
      ZenDeskCustomField(fieldIds.os, getOsForZenDesk()),
      ZenDeskCustomField(fieldIds.timezone, System.getProperty("user.timezone")),
      LicensingFacade.getInstance()?.isEvaluationLicense?.let { isEval ->
        ZenDeskCustomField(fieldIds.eval, isEval.toString())
      },
      ZenDeskCustomField(fieldIds.systemInfo, customFieldValues.systemInfo),
      ZenDeskCustomField(fieldIds.needSupport,
                         if (customFieldValues.needSupport) "share_problems_or_ask_about_missing_features" else "provide_a_feedback"),
      customFieldValues.topicId?.let { topicId ->
        ZenDeskCustomField(fieldIds.topic, topicId)
      }
    )

    val request = ZenDeskRequest(
      ZenDeskRequester("anonymous", email),
      subject,
      ZenDeskComment(text),
      zenDeskFormData.formId,
      customFields
    )
    val requestData = objectMapper.writeValueAsString(mapOf("request" to request))
    try {
      HttpRequests
        .post("${zenDeskFormData.formUrl}/api/v2/requests", "application/json")
        .productNameAsUserAgent()
        .accept("application/json")
        .connect {
          try {
            it.write(requestData)
            val bytes = it.inputStream.readAllBytes()
            LOG.info(bytes.toString(Charsets.UTF_8))
          }
          catch (e: IOException) {
            val errorResponse = (it.connection as HttpURLConnection).errorStream?.readAllBytes()?.toString(Charsets.UTF_8)
            LOG.info("Failed to submit feedback. Feedback data:\n$requestData\n" +
                     "Server response:\n$errorResponse\n" +
                     "Exception:\n${e.stackTraceToString()}")
            onError()
            return@connect
          }
          onDone()
        }
    } catch (e: IOException) {
      LOG.info("Failed to submit feedback. Feedback data:\n$requestData\nError message:\n${e.message}")
      onError()
      return
    }
  }

  private fun getOsForZenDesk(): String {
    return when {
      SystemInfo.isWindows -> "ij_win"
      SystemInfo.isMac -> "ij_mac"
      SystemInfo.isLinux -> "ij_linux"
      else -> "ij_other-os"
    }
  }

  companion object {
    private val LOG = Logger.getInstance(ZenDeskRequests::class.java)
  }
}

internal data class CustomFieldValues(
  val systemInfo: String,
  val needSupport: Boolean,
  val rating: Int?,
  val topicId: String?
)

private class ZenDeskComment(val body: String)

private class ZenDeskRequester(val name: String, val email: String)

private class ZenDeskCustomField(val id: Long, val value: Any)

private class ZenDeskRequest(
  val requester: ZenDeskRequester,
  val subject: String,
  val comment: ZenDeskComment,
  val ticket_form_id: Long,
  val custom_fields: List<ZenDeskCustomField>
)
