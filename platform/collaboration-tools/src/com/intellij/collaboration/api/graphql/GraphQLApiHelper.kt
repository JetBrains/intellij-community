// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.graphql

import com.intellij.collaboration.api.HttpApiHelper
import com.intellij.collaboration.api.dto.GraphQLRequestDTO
import com.intellij.collaboration.api.dto.getOrThrow
import com.intellij.collaboration.api.httpclient.ByteArrayProducingBodyPublisher
import com.intellij.collaboration.api.httpclient.HttpClientUtil
import com.intellij.collaboration.api.httpclient.HttpClientUtil.inflateAndReadWithErrorHandlingAndLogging
import com.intellij.collaboration.api.json.HttpJsonDeserializationException
import com.intellij.collaboration.api.json.JsonDataSerializer
import com.intellij.collaboration.api.logName
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.annotations.ApiStatus
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@ApiStatus.Experimental
interface GraphQLApiHelper {
  fun query(uri: URI, loadQuery: () -> String, variablesObject: Any? = null): HttpRequest

  suspend fun <T> loadResponseByClass(request: HttpRequest, clazz: Class<T>, vararg pathFromData: String): HttpResponse<out T?>
}

@ApiStatus.Experimental
suspend inline fun <reified T> GraphQLApiHelper.loadResponse(request: HttpRequest, vararg pathFromData: String): HttpResponse<out T?> =
  loadResponseByClass(request, T::class.java, *pathFromData)


@ApiStatus.Experimental
fun GraphQLApiHelper(logger: Logger,
                     httpHelper: HttpApiHelper,
                     serializer: JsonDataSerializer,
                     deserializer: GraphQLDataDeserializer): GraphQLApiHelper =
  GraphQLApiHelperImpl(logger, httpHelper, serializer, deserializer)

private class GraphQLApiHelperImpl(private val logger: Logger,
                                   private val httpHelper: HttpApiHelper,
                                   private val serializer: JsonDataSerializer,
                                   private val deserializer: GraphQLDataDeserializer)
  : GraphQLApiHelper, HttpApiHelper by httpHelper {

  override fun query(uri: URI, loadQuery: () -> String, variablesObject: Any?): HttpRequest {
    val publisher = ByteArrayProducingBodyPublisher {
      logger.debug("GraphQL request $uri")
      val query = loadQuery()
      val request = GraphQLRequestDTO(query, variablesObject)
      val jsonBytes = serializer.toJsonBytes(request)
      if (logger.isTraceEnabled) {
        logger.trace("GraphQL request $uri : Request body: " + String(jsonBytes, Charsets.UTF_8))
      }
      jsonBytes
    }

    return httpHelper.request(uri)
      .POST(publisher)
      .header(HttpClientUtil.CONTENT_TYPE_HEADER, HttpClientUtil.CONTENT_TYPE_JSON)
      .build()
  }

  override suspend fun <T> loadResponseByClass(request: HttpRequest, clazz: Class<T>, vararg pathFromData: String): HttpResponse<out T?> {
    val handler = inflateAndReadWithErrorHandlingAndLogging(logger, request) { reader, _ ->
      val result = try {
        deserializer.readAndMapGQLResponse(reader, pathFromData, clazz)
      }
      catch (e: Throwable) {
        logger.warn("API response deserialization failed", e)
        throw HttpJsonDeserializationException(request.logName(), e)
      }
      result.getOrThrow()
    }
    return httpHelper.sendAndAwaitCancellable(request, handler)
  }
}