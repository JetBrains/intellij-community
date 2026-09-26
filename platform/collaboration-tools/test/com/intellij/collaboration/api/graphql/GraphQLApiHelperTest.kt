// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.graphql

import com.intellij.collaboration.api.HttpApiHelper
import com.intellij.collaboration.api.HttpStatusErrorException
import com.intellij.collaboration.api.TestGraphQLDataDeserializer
import com.intellij.collaboration.api.TestJsonDataSerializer
import com.intellij.collaboration.api.json.HttpJsonDeserializationException
import com.intellij.collaboration.api.newRecorder
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

data class GqlNode(val id: String, val title: String)

/**
 * Integration tests for [GraphQLApiHelper] running against a local [HttpServer].
 */
@TestFixtures
class GraphQLApiHelperTest {
  private val serverFixture: TestFixture<HttpServer> = localhostHttpServer()
  private val server: HttpServer get() = serverFixture.get()

  private val httpHelper: HttpApiHelper = testHttpApiHelper()
  private val gqlHelper: GraphQLApiHelper =
    GraphQLApiHelper(LOG, httpHelper, TestJsonDataSerializer, TestGraphQLDataDeserializer)

  @Test
  fun `query builds a POST request carrying the query and variables`() = timeoutRunBlocking {
    val recorder = newRecorder()
    server.respondWithJson(json = """{"data":{"node":{"id":"1","title":"t"}}}""", recorder = recorder)

    val request = gqlHelper.query(URI.create(server.url), { "query { node { id title } }" }, mapOf("id" to "1"))
    gqlHelper.loadResponseByClass(request, GqlNode::class.java, "node")

    val recorded = recorder.single()
    assertThat(recorded.method).isEqualTo("POST")
    assertThat(recorded.header("Content-Type")).isEqualTo("application/json")
    assertThat(recorded.body).contains("query { node { id title } }")
    assertThat(recorded.body).contains(""""id":"1"""")
    Unit
  }

  @Test
  fun `loadResponse sends the JSON accept header`() = timeoutRunBlocking {
    val recorder = newRecorder()
    server.respondWithJson(json = """{"data":{"node":{"id":"1","title":"t"}}}""", recorder = recorder)

    val request = gqlHelper.query(URI.create(server.url), { "q" })
    gqlHelper.loadResponseByClass(request, GqlNode::class.java, "node")

    assertThat(recorder.single().header("Accept")).isEqualTo("application/json")
    Unit
  }

  @Test
  fun `loadResponse deserializes the data at the given path`() = timeoutRunBlocking {
    server.respondWithJson(json = """{"data":{"node":{"id":"42","title":"hello"}}}""")

    val request = gqlHelper.query(URI.create(server.url), { "q" })
    val response = gqlHelper.loadResponseByClass(request, GqlNode::class.java, "node")

    assertThat(response.body()).isEqualTo(GqlNode("42", "hello"))
    Unit
  }

  @Test
  fun `loadResponse traverses a nested path`() = timeoutRunBlocking {
    server.respondWithJson(json = """{"data":{"project":{"issue":{"id":"7","title":"nested"}}}}""")

    val request = gqlHelper.query(URI.create(server.url), { "q" })
    val response = gqlHelper.loadResponseByClass(request, GqlNode::class.java, "project", "issue")

    assertThat(response.body()).isEqualTo(GqlNode("7", "nested"))
    Unit
  }

  @Test
  fun `loadResponse returns null when the path resolves to null`() = timeoutRunBlocking {
    server.respondWithJson(json = """{"data":{"node":null}}""")

    val request = gqlHelper.query(URI.create(server.url), { "q" })
    val response = gqlHelper.loadResponseByClass(request, GqlNode::class.java, "node")

    assertThat(response.body()).isNull()
    Unit
  }

  @Test
  fun `loadResponse throws GraphQLErrorException when the response has errors and no data`() = timeoutRunBlocking {
    server.respondWithJson(json = """{"data":null,"errors":[{"message":"boom"}]}""")

    val request = gqlHelper.query(URI.create(server.url), { "q" })
    val error = runCatching { gqlHelper.loadResponseByClass(request, GqlNode::class.java) }.exceptionOrNull()

    assertThat(error).isInstanceOf(GraphQLErrorException::class.java)
    error as GraphQLErrorException
    assertThat(error.errors.map { it.message }).containsExactly("boom")
    Unit
  }

  @Test
  fun `loadResponse wraps a deserialization failure`() = timeoutRunBlocking {
    server.respondWithJson(json = "not valid json")

    val request = gqlHelper.query(URI.create(server.url), { "q" })
    val error = runCatching { gqlHelper.loadResponseByClass(request, GqlNode::class.java, "node") }.exceptionOrNull()

    assertThat(error).isInstanceOf(HttpJsonDeserializationException::class.java)
    Unit
  }

  @Test
  fun `an error status code is reported as HttpStatusErrorException`() = timeoutRunBlocking {
    server.respondWithText(status = 500, text = "boom")

    val request = gqlHelper.query(URI.create(server.url), { "q" })
    val error = runCatching { gqlHelper.loadResponseByClass(request, GqlNode::class.java, "node") }.exceptionOrNull()

    assertThat(error).isInstanceOf(HttpStatusErrorException::class.java)
    error as HttpStatusErrorException
    assertThat(error.statusCode).isEqualTo(500)
    Unit
  }

  companion object {
    private val LOG = Logger.getInstance(GraphQLApiHelperTest::class.java)
  }
}
