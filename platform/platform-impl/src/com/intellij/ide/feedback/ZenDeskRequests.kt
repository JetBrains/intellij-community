// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.feedback

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.io.HttpRequests

class ZenDeskRequests {
  private val objectMapper by lazy { ObjectMapper() }

  fun submit(email: String, subject: String, text: String, rating: Int) {
    val request = ZenDeskRequest(
      ZenDeskRequester("anonymous", email),
      subject,
      ZenDeskComment(text),
      TICKET_FORM_ID,
      listOf(ZenDeskCustomField(RATING_FIELD_ID, rating))
    )
    val requestData = objectMapper.writeValueAsString(mapOf("request" to request))
    HttpRequests
      .post("https://jbs.zendesk.com/api/v2/requests", "application/json")
      .productNameAsUserAgent()
      .accept("application/json")
      .connect {
        it.write(requestData)
        val bytes = it.inputStream.readAllBytes()
        LOG.info(bytes.toString(Charsets.UTF_8))
      }
  }

  companion object {
    private const val TICKET_FORM_ID = 68704
    private const val RATING_FIELD_ID = 29444529
    private val LOG = Logger.getInstance(ZenDeskRequests::class.java)
  }
}

private class ZenDeskComment(val body: String)

private class ZenDeskRequester(val name: String, val email: String)

private class ZenDeskCustomField(val id: Int, val value: Any)

private class ZenDeskRequest(
  val requester: ZenDeskRequester,
  val subject: String,
  val comment: ZenDeskComment,
  val ticket_form_id: Int,
  val custom_fields: List<ZenDeskCustomField>
)
