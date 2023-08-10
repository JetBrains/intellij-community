// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.ide.actions.SettingsEntryPointAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ui.UIUtil
import io.netty.handler.codec.http.HttpResponseStatus
import org.assertj.core.api.Assertions
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.event.KeyEvent
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.JPanel
import kotlin.concurrent.thread

private abstract class ToolboxServiceHandlerX : ToolboxServiceHandler<JsonElement> {
  override val requestName: String = "update-notification"
  override fun parseRequest(request: JsonElement): JsonElement = request
}

internal class ToolboxUpdateServiceTest {
  private val notifyUpdatePayload = "{'build': '123', 'version': '2021.1 RC'}"
  private val notifyUpdateAction = "update-notification"

  private val notifyRestartPayload = "{'build': '123', 'version': '2021.1 RC'}"
  private val notifyRestartAction = "restart-notification"

  @Rule
  @JvmField
  val appRule = ApplicationRule()

  @Before
  fun runServer() {
    BuiltInServerManager.getInstance().waitForStart()
  }

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
  fun testRestartAction() = doActionTest(notifyRestartAction, notifyRestartPayload) {
    Assert.assertEquals("$actions", 1, actions.size)
    val theAction = actions.single()

    runAction(theAction)
    doEventsWhile(disconnected)

    println("payload: $payload")

    val json = JsonParser.parseString(payload)
    Assert.assertTrue(json.isJsonObject)
    Assert.assertEquals("accepted", json.asJsonObject["status"]?.asString)
    Assert.assertEquals(ProcessHandle.current().pid(), json.asJsonObject["pid"]?.asLong)

    doEventsWhile(iterations = 10) { actions.isNotEmpty() }
    Assert.assertEquals("$actions", 0, actions.size)

    val cond = { System.getProperty("toolbox-service-test-restart") == "1" }
    doEventsWhile(iterations = 10) { !cond() }
    Assert.assertTrue("Application must be disposed", cond())
  }

  @Test
  fun testUpdateAction() = doActionTest(notifyUpdateAction, notifyRestartPayload) {
    Assert.assertEquals("$actions", 1, actions.size)
    val theAction = actions.single()

    runAction(theAction)
    doEventsWhile(disconnected)
    println("payload: $payload")

    val json = JsonParser.parseString(payload)
    Assert.assertTrue(json.isJsonObject)
    Assert.assertEquals("accepted", json.asJsonObject["status"]?.asString)

    doEventsWhile(10) { actions.isNotEmpty() }
    Assert.assertEquals("$actions", 0, actions.size)
  }

  private class ToolboxActionFixture {
    val connected = CountDownLatch(1)
    val disconnected = CountDownLatch(1)
    lateinit var payload: String

    val actions
      get() = SettingsEntryPointAction.ActionProvider.EP_NAME
        .extensionList.filterIsInstance<ToolboxSettingsActionRegistryActionProvider>()
        .flatMap {
          it.getUpdateActions(DataContext.EMPTY_CONTEXT)
        }
  }

  private fun doActionTest(requestName: String,
                           requestPayload: String,
                           logic: ToolboxActionFixture.() -> Unit) {
    val f = ToolboxActionFixture()
    val thread = thread {
      doToolbox(
        action = requestName, payload = requestPayload,
        includeToken = true, responseStatus = HttpResponseStatus.OK
      ) {
        f.connected.countDown()
        try {
          f.payload = it.body().readAllBytes().toString(Charsets.UTF_8)
        }
        finally {
          f.disconnected.countDown()
        }
      }
    }

    try {
      doEventsWhile(f.connected)
      doEventsWhile(10) { f.actions.isEmpty()  }
      return f.logic()
    }
    finally {
      thread.interrupt()
      thread.join()
    }
  }

  @Test
  fun testShouldCheckToken_withToken_beat_and_response() {
    val condition = CountDownLatch(10)

    val ext = object : ToolboxServiceHandlerX() {
      override fun handleToolboxRequest(lifetime: Disposable, request: JsonElement, onResult: (JsonElement) -> Unit) {
        try {
          condition.await()
        } finally {
          onResult(JsonObject().apply { addProperty("status", "abc")})
        }
      }
    }

    withExtension(ext) {
      doToolbox(
        action = ext.requestName,
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
    val ext = object : ToolboxServiceHandlerX() {
      override fun handleToolboxRequest(lifetime: Disposable, request: JsonElement, onResult: (JsonElement) -> Unit) {
        Disposer.register(lifetime) { condition.countDown() }
      }
    }

    withExtension(ext) {
      doToolbox(
        action = ext.requestName,
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

  private fun withExtension(handler: ToolboxServiceHandler<*>, action: () -> Unit) {
    val d = Disposer.newDisposable()
    try {
      ExtensionTestUtil.maskExtensions(toolboxHandlerEP, listOf(handler), d)
      return action()
    } finally {
      Disposer.dispose(d)
    }
  }

  private fun doToolbox(action: String,
                        payload: String = " { } ",
                        includeToken: Boolean = true,
                        responseStatus: HttpResponseStatus,
                        body : (HttpResponse<InputStream>) -> Unit = {}
  ) {
    val token = UUID.randomUUID().toString()
    val url = "http://localhost:${BuiltInServerManager.getInstance().port}/api/toolbox/$action"

    PlatformTestUtil.withSystemProperty<Nothing>("toolbox.heartbeat.millis", "10") {
      PlatformTestUtil.withSystemProperty<Nothing>("toolbox.notification.token", token) {
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
        }
        finally {
          runCatching { response.body()?.close() }
        }
      }
    }
  }
}

private fun doEventsWhile(latch: CountDownLatch) = doEventsWhile {
  !latch.await(10, TimeUnit.MILLISECONDS)
}

private fun doEventsWhile(iterations: Int = Int.MAX_VALUE / 2,
                          condition: () -> Boolean = { true }) {
  repeat(iterations) {
    if (!condition()) return

    ApplicationManager.getApplication().invokeAndWait {
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
      UIUtil.dispatchAllInvocationEvents()
    }

    if (!condition()) return
    Thread.sleep(30)
  }
}

private fun runAction(theAction: AnAction) {
  ApplicationManager.getApplication().invokeAndWait {
    val event = KeyEvent(JPanel(), 1, 0, 0, 0, ' ')
    ActionManager.getInstance().tryToExecute(theAction, event, null, null, true)
  }
}
