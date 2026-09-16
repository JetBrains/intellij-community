// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.graphql

import com.intellij.collaboration.api.HttpApiHelper
import com.intellij.collaboration.api.HttpStatusErrorException
import com.intellij.collaboration.api.dto.GraphQLRequestDTO
import com.intellij.collaboration.api.dto.getOrThrow
import com.intellij.collaboration.api.httpclient.HttpClientUtil
import com.intellij.collaboration.api.httpclient.ensureAcceptHeader
import com.intellij.collaboration.api.json.HttpJsonDeserializationException
import com.intellij.collaboration.api.json.HttpJsonSerializationException
import com.intellij.collaboration.api.json.JsonDataSerializer
import com.intellij.collaboration.api.sendAndRead
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@ApiStatus.Experimental
interface GraphQLApiHelper {
  @Throws(HttpJsonSerializationException::class)
  suspend fun query(uri: URI, loadQuery: () -> String, variablesObject: Any? = null): HttpRequest

  @Throws(IOException::class, HttpStatusErrorException::class, HttpJsonDeserializationException::class, GraphQLErrorException::class)
  suspend fun <T : Any> loadResponseByClass(request: HttpRequest, clazz: Class<T>, vararg pathFromData: String): HttpResponse<out T?>
}

@ApiStatus.Experimental
@Throws(IOException::class, HttpStatusErrorException::class, HttpJsonDeserializationException::class, GraphQLErrorException::class)
context(api: GraphQLApiHelper)
suspend inline fun <reified T : Any> HttpRequest.loadResponse(vararg pathFromData: String): HttpResponse<out T?> =
  api.loadResponseByClass(this, T::class.java, *pathFromData)


@ApiStatus.Experimental
fun GraphQLApiHelper(
  logger: Logger,
  httpHelper: HttpApiHelper,
  serializer: JsonDataSerializer,
  deserializer: GraphQLDataDeserializer,
): GraphQLApiHelper =
  GraphQLApiHelperImpl(logger, httpHelper, serializer, deserializer)

private class GraphQLApiHelperImpl(
  private val logger: Logger,
  private val httpHelper: HttpApiHelper,
  private val serializer: JsonDataSerializer,
  private val deserializer: GraphQLDataDeserializer,
) : GraphQLApiHelper, HttpApiHelper by httpHelper {

  @Throws(HttpJsonSerializationException::class)
  override suspend fun query(uri: URI, loadQuery: () -> String, variablesObject: Any?): HttpRequest {
    val query = withContext(Dispatchers.IO) {
      loadQuery()
    }
    val request = GraphQLRequestDTO(query, variablesObject)
    val requestName = gqlRequestName(uri)
    val jsonBytes = withContext(Dispatchers.Default) {
      try {
        serializer.toJsonBytes(request)
      }
      catch (e: Exception) {
        logger.warn("GraphQL API request serialization failed", e)
        throw HttpJsonSerializationException(requestName, e)
      }
    }
    if (logger.isTraceEnabled) {
      logger.trace("$requestName : Request body: " + String(jsonBytes, Charsets.UTF_8))
    }
    return httpHelper.request(uri)
      .POST(HttpRequest.BodyPublishers.ofByteArray(jsonBytes))
      .header(HttpClientUtil.CONTENT_TYPE_HEADER, HttpClientUtil.MIME_TYPE_JSON)
      .build()
  }

  override suspend fun <T : Any> loadResponseByClass(
    request: HttpRequest,
    clazz: Class<T>,
    vararg pathFromData: String,
  ): HttpResponse<out T?> {
    val jsonRequest = request.ensureAcceptHeader(HttpClientUtil.MIME_TYPE_JSON)
    val requestName = request.gqlRequestName()
    return sendAndRead(jsonRequest, requestName) { _, charset ->
      // check mime type?
      val result = try {
        deserializer.readAndMapGQLResponse(this, charset ?: Charsets.UTF_8, pathFromData, clazz)
      }
      catch (e: Throwable) {
        logger.warn("API response deserialization failed", e)
        throw HttpJsonDeserializationException(requestName, e)
      }
      result.getOrThrow()
    }
  }
}

private fun gqlRequestName(uri: URI): String = "GraphQL request POST $uri"
private fun HttpRequest.gqlRequestName(): String = gqlRequestName(uri())
