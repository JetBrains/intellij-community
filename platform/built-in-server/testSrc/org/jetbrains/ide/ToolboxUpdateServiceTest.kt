// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide

import com.google.gson.JsonParser
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ExtensionTestUtil
import io.netty.handler.codec.http.HttpResponseStatus
import org.assertj.core.api.Assertions
import org.junit.Assert
import org.junit.Test
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class ToolboxUpdateServiceTest : BuiltInServerTestCase() {
  private val notifyUpdatePayload = "{'build': '123', 'version': '2021.1 RC'}"
  private val notifyUpdateAction = "update-notification"

  @Test
  fun testShouldCheckToken_noToken() {
    doToolbox(
      action = notifyUpdateAction, payload = notifyUpdatePayload,
      includeToken = false, responseStatus = HttpResponseStatus.NOT_FOUND)
  }

  @Test
  fun testShouldCheckToken_withToken() {
    doToolbox(
      action = notifyUpdateAction, payload = notifyUpdatePayload,
      includeToken = true, responseStatus = HttpResponseStatus.OK
    )
  }

  @Test
  fun testShouldCheckToken_withToken_beat_and_response() {
    val condition = CountDownLatch(10)

    val ext = object : ToolboxServiceHandler {
      override fun handleToolboxRequest(lifetime: Disposable, request: ToolboxActionRequest, onResult: (ToolboxActionResult) -> Unit) {
        try {
          condition.await()
        } finally {
          onResult(ToolboxActionResult.SimpleResult("abc"))
        }
      }
    }

    withExtension(ext) {
      doToolbox(
        action = notifyUpdateAction, payload = notifyUpdatePayload,
        includeToken = true, responseStatus = HttpResponseStatus.OK,
      ) { response ->
        val body = response.body()
        while (true) {
          val r = body.read()
          if (r < 0) continue
          Assert.assertTrue("It must not return anything else but a whitespace:  " + r.toChar(), r.toChar().isWhitespace())

          condition.countDown()
          if (condition.await(1, TimeUnit.NANOSECONDS)) break
        }

        val message = body.readAllBytes().toString(StandardCharsets.UTF_8)
        val json = JsonParser.parseString(message)
        Assert.assertTrue(json.isJsonObject)
        Assert.assertEquals("abc", json.asJsonObject["status"]?.asString)
      }
    }
  }

  @Test
  fun testShouldCheckToken_withToken_beat_client_close() {
    val condition = CountDownLatch(1)
    val ext = object : ToolboxServiceHandler {
      override fun handleToolboxRequest(lifetime: Disposable, request: ToolboxActionRequest, onResult: (ToolboxActionResult) -> Unit) {
        Disposer.register(lifetime) { condition.countDown() }
      }
    }

    withExtension(ext) {
      doToolbox(
        action = notifyUpdateAction, payload = notifyUpdatePayload,
        includeToken = true, responseStatus = HttpResponseStatus.OK,
      ) { response ->
        var counter = 10
        val body = response.body()
        while (counter --> 0) {
          val r = body.read()
          if (r < 0) continue
          Assert.assertTrue("It must not return anything else but a whitespace:  " + r.toChar(), r.toChar().isWhitespace())
        }
        body.close()
      }
    }

    condition.await()
  }

  private fun withExtension(handler: ToolboxServiceHandler, action: () -> Unit) {
    val d = Disposer.newDisposable()
    try {
      ExtensionTestUtil.maskExtensions(toolboxHandlerEP, listOf(handler), d)
      return action()
    } finally {
      Disposer.dispose(d)
    }
  }

  private fun doToolbox(action: String,
                        payload: String,
                        includeToken: Boolean = true,
                        responseStatus: HttpResponseStatus,
                        body : (HttpResponse<InputStream>) -> Unit = {}
  ) {
    val token = UUID.randomUUID().toString()
    val url = "http://localhost:${BuiltInServerManager.getInstance().port}/api/toolbox/$action"

    withSystemProperty("toolbox.heartbeat.millis", "10") {
      withSystemProperty("toolbox.notification.token", token) {
        val builder = HttpRequest.newBuilder(URI(url))
        if (includeToken) {
          builder.header("Authorization", "toolbox $token")
        }
        builder.POST(HttpRequest.BodyPublishers.ofString(payload))

        val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()
        val response: HttpResponse<InputStream> = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream())
        Assertions.assertThat(HttpResponseStatus.valueOf(response.statusCode())).isEqualTo(responseStatus)
        try {
          body(response)
        } finally {
          runCatching { response.body()?.close() }
        }
      }
    }
  }
}

private inline fun <Y> withSystemProperty(key: String, value: String, action: () -> Y): Y {
  val old = System.getProperty(key)
  System.setProperty(key, value)
  try {
    return action()
  }
  finally {
    if (old == null) {
      System.clearProperty(key)
    }
    else {
      System.setProperty(key, old)
    }
  }
}
