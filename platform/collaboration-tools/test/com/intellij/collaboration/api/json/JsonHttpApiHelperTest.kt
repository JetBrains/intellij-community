// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.json

import com.intellij.collaboration.api.EmptyHttpResponseException
import com.intellij.collaboration.api.HttpApiHelper
import com.intellij.collaboration.api.TestJsonDataSerializer
import com.intellij.collaboration.api.newRecorder
import com.intellij.collaboration.api.request
import com.intellij.collaboration.api.respondWithJson
import com.intellij.collaboration.api.respondWithText
import com.intellij.collaboration.api.testHttpApiHelper
import com.intellij.openapi.diagnostic.Logger
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.TestFixtures
import com.intellij.testFramework.junit5.http.localhostHttpServer
import com.intellij.testFramework.junit5.http.url
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URI

data class TestItem(val id: Int, val name: String)

/**
 * Integration tests for [JsonHttpApiHelper] running against a local [HttpServer].
 */
@TestFixtures
class JsonHttpApiHelperTest {
  private val serverFixture: TestFixture<HttpServer> = localhostHttpServer()
  private val server: HttpServer get() = serverFixture.get()

  private val httpHelper: HttpApiHelper = testHttpApiHelper()
  private val jsonHelper: JsonHttpApiHelper =
    JsonHttpApiHelper(LOG, httpHelper, TestJsonDataSerializer, TestJsonDataSerializer)

  private suspend fun request() = httpHelper.request(server.url).build()

  @Test
  fun `loadJsonValue deserializes a single object`() = timeoutRunBlocking {
    server.respondWithJson(json = """{"id":1,"name":"foo"}""")

    val response = jsonHelper.loadJsonValueByClass(request(), TestItem::class.java)

    assertThat(response.statusCode()).isEqualTo(200)
    assertThat(response.body()).isEqualTo(TestItem(1, "foo"))
    Unit
  }

  @Test
  fun `loadJsonValue sends the JSON accept header`() = timeoutRunBlocking {
    val recorder = newRecorder()
    server.respondWithJson(json = """{"id":1,"name":"foo"}""", recorder = recorder)

    jsonHelper.loadJsonValueByClass(request(), TestItem::class.java)

    assertThat(recorder.single().header("Accept")).isEqualTo("application/json")
    Unit
  }

  @Test
  fun `loadJsonValue throws EmptyHttpResponseException on an empty body`() = timeoutRunBlocking {
    server.respondWithJson(json = "")

    val error = runCatching { jsonHelper.loadJsonValueByClass(request(), TestItem::class.java) }.exceptionOrNull()

    assertThat(error).isInstanceOf(EmptyHttpResponseException::class.java)
    Unit
  }

  @Test
  fun `loadOptionalJsonValue returns null on an empty body`() = timeoutRunBlocking {
    server.respondWithJson(json = "")

    val response = jsonHelper.loadOptionalJsonValueByClass(request(), TestItem::class.java)

    assertThat(response.body()).isNull()
    Unit
  }

  @Test
  fun `loadJsonList deserializes a list`() = timeoutRunBlocking {
    server.respondWithJson(json = """[{"id":1,"name":"a"},{"id":2,"name":"b"}]""")

    val response = jsonHelper.loadJsonListByClass(request(), TestItem::class.java)

    assertThat(response.body()).containsExactly(TestItem(1, "a"), TestItem(2, "b"))
    Unit
  }

  @Test
  fun `loadOptionalJsonList returns null on an empty body`() = timeoutRunBlocking {
    server.respondWithJson(json = "")

    val response = jsonHelper.loadOptionalJsonListByClass(request(), TestItem::class.java)

    assertThat(response.body()).isNull()
    Unit
  }

  @Test
  fun `loadJsonValue rejects a non-JSON mime type`() = timeoutRunBlocking {
    server.respondWithText(text = """{"id":1,"name":"foo"}""", contentType = "text/plain; charset=UTF-8")

    val error = runCatching { jsonHelper.loadJsonValueByClass(request(), TestItem::class.java) }.exceptionOrNull()

    assertThat(error).isInstanceOf(IllegalStateException::class.java)
    assertThat(error).hasMessageContaining("JSON mime type expected")
    Unit
  }

  @Test
  fun `loadJsonValue wraps a deserialization failure`() = timeoutRunBlocking {
    server.respondWithJson(json = "{ this is not valid json ")

    val error = runCatching { jsonHelper.loadJsonValueByClass(request(), TestItem::class.java) }.exceptionOrNull()

    assertThat(error).isInstanceOf(HttpJsonDeserializationException::class.java)
    Unit
  }

  @Test
  fun `postJson serializes the body and sets the JSON content type`() = timeoutRunBlocking {
    val recorder = newRecorder()
    server.respondWithJson(json = """{"id":9,"name":"created"}""", recorder = recorder)

    val request = jsonHelper.postJson(URI.create(server.url), TestItem(9, "created")).build()
    val response = jsonHelper.loadJsonValueByClass(request, TestItem::class.java)

    val recorded = recorder.single()
    assertThat(recorded.method).isEqualTo("POST")
    assertThat(recorded.header("Content-Type")).isEqualTo("application/json")
    assertThat(recorded.body).isEqualTo("""{"id":9,"name":"created"}""")
    assertThat(response.body()).isEqualTo(TestItem(9, "created"))
    Unit
  }

  @Test
  fun `putJson uses the PUT method`() = timeoutRunBlocking {
    val recorder = newRecorder()
    server.respondWithJson(json = """{"id":1,"name":"x"}""", recorder = recorder)

    val request = jsonHelper.putJson(URI.create(server.url), TestItem(1, "x")).build()
    jsonHelper.loadOptionalJsonValueByClass(request, TestItem::class.java)

    assertThat(recorder.single().method).isEqualTo("PUT")
    Unit
  }

  @Test
  fun `sendJson uses the given HTTP method`() = timeoutRunBlocking {
    val recorder = newRecorder()
    server.respondWithJson(json = """{"id":1,"name":"x"}""", recorder = recorder)

    val request = jsonHelper.sendJson(URI.create(server.url), "PATCH", TestItem(1, "x")).build()
    jsonHelper.loadOptionalJsonValueByClass(request, TestItem::class.java)

    assertThat(recorder.single().method).isEqualTo("PATCH")
    Unit
  }

  @Test
  fun `loadOptionalJsonValue supports ETag polling with a 304 empty response`() = timeoutRunBlocking {
    val etag = "\"v1\""
    server.respondWithConditionalJson(etag, """{"id":1,"name":"foo"}""")

    // first poll: full response carrying the ETag
    val firstResponse = jsonHelper.loadOptionalJsonValueByClass(request(), TestItem::class.java)
    assertThat(firstResponse.statusCode()).isEqualTo(200)
    assertThat(firstResponse.body()).isEqualTo(TestItem(1, "foo"))
    val receivedEtag = firstResponse.headers().firstValue("ETag").orElse(null)
    assertThat(receivedEtag).isEqualTo(etag)

    // second poll: nothing changed, the empty 304 is surfaced as a null body without throwing
    val secondResponse = jsonHelper.loadOptionalJsonValueByClass(conditionalRequest(receivedEtag), TestItem::class.java)
    assertThat(secondResponse.statusCode()).isEqualTo(304)
    assertThat(secondResponse.body()).isNull()
    Unit
  }

  @Test
  fun `loadOptionalJsonList supports ETag polling with a 304 empty response`() = timeoutRunBlocking {
    // this is the exact path GitLab's conditional page loading relies on (getJsonListConditional -> loadOptionalJsonList)
    val etag = "\"v1\""
    server.respondWithConditionalJson(etag, """[{"id":1,"name":"a"},{"id":2,"name":"b"}]""")

    // first poll: full response carrying the ETag
    val firstResponse = jsonHelper.loadOptionalJsonListByClass(request(), TestItem::class.java)
    assertThat(firstResponse.statusCode()).isEqualTo(200)
    assertThat(firstResponse.body()).containsExactly(TestItem(1, "a"), TestItem(2, "b"))
    assertThat(firstResponse.headers().firstValue("ETag").orElse(null)).isEqualTo(etag)

    // second poll: nothing changed, the empty 304 is surfaced as a null body without throwing
    val secondResponse = jsonHelper.loadOptionalJsonListByClass(conditionalRequest(etag), TestItem::class.java)
    assertThat(secondResponse.statusCode()).isEqualTo(304)
    assertThat(secondResponse.body()).isNull()
    Unit
  }

  private suspend fun conditionalRequest(etag: String) =
    httpHelper.request(server.url).header("If-None-Match", etag).build()

  /**
   * Registers a conditional-GET endpoint: it serves [json] with the given [etag] on an unconditional request, and
   * replies with an empty `304 Not Modified` (carrying no `Content-Type`) when the client presents the matching ETag.
   */
  private fun HttpServer.respondWithConditionalJson(etag: String, json: String) {
    val payload = json.toByteArray(Charsets.UTF_8)
    createContext("/") { exchange ->
      exchange.use { exchange ->
        exchange.requestBody.use { it.readBytes() }
        if (exchange.requestHeaders.getFirst("If-None-Match") == etag) {
          exchange.sendResponseHeaders(304, -1)
        }
        else {
          exchange.responseHeaders.add("ETag", etag)
          exchange.responseHeaders.add("Content-Type", "application/json")
          exchange.sendResponseHeaders(200, payload.size.toLong())
          exchange.responseBody.write(payload)
        }
      }
    }
  }

  companion object {
    private val LOG = Logger.getInstance(JsonHttpApiHelperTest::class.java)
  }
}
