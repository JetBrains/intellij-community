// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.graphql

import com.intellij.collaboration.api.HttpApiHelper
import com.intellij.collaboration.api.dto.GraphQLRequestDTO
import com.intellij.collaboration.api.httpclient.ByteArrayProducingBodyPublisher
import com.intellij.collaboration.api.httpclient.HttpClientUtil
import com.intellij.collaboration.api.httpclient.InflatedStreamReadingBodyHandler
import com.intellij.collaboration.api.json.JsonDataSerializer
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.annotations.ApiStatus
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@ApiStatus.Experimental
interface GraphQLApiHelper {
  fun gqlQuery(uri: URI, queryPath: String, variablesObject: Any? = null): HttpRequest

  suspend fun <T> loadGQLResponse(request: HttpRequest, clazz: Class<T>, vararg pathFromData: String): HttpResponse<out T?>
}

@ApiStatus.Experimental
suspend inline fun <reified T> GraphQLApiHelper.loadGQLResponse(request: HttpRequest, vararg pathFromData: String): HttpResponse<out T?> =
  loadGQLResponse(request, T::class.java, *pathFromData)


@ApiStatus.Experimental
fun GraphQLApiHelper(logger: Logger,
                     httpHelper: HttpApiHelper,
                     queryLoader: CachingGraphQLQueryLoader,
                     serializer: JsonDataSerializer,
                     deserializer: GraphQLDataDeserializer): GraphQLApiHelper =
  GraphQLApiHelperImpl(logger, httpHelper, queryLoader, serializer, deserializer)

private class GraphQLApiHelperImpl(private val logger: Logger,
                                   private val httpHelper: HttpApiHelper,
                                   private val queryLoader: CachingGraphQLQueryLoader,
                                   private val serializer: JsonDataSerializer,
                                   private val deserializer: GraphQLDataDeserializer)
  : GraphQLApiHelper {

  override fun gqlQuery(uri: URI, queryPath: String, variablesObject: Any?): HttpRequest {
    val publisher = ByteArrayProducingBodyPublisher {
      logger.debug("Request POST $uri")
      val query = queryLoader.loadQuery(queryPath)
      val request = GraphQLRequestDTO(query, variablesObject)
      val jsonBytes = serializer.toJsonBytes(request)
      if (logger.isTraceEnabled) {
        logger.trace("Request POST $uri : Request body: " + String(jsonBytes, Charsets.UTF_8))
      }
      jsonBytes
    }

    return httpHelper.request(uri)
      .POST(publisher)
      .header(HttpClientUtil.CONTENT_TYPE_HEADER, HttpClientUtil.CONTENT_TYPE_JSON)
      .build()
  }

  override suspend fun <T> loadGQLResponse(request: HttpRequest, clazz: Class<T>, vararg pathFromData: String): HttpResponse<out T?> {
    val handler = InflatedStreamReadingBodyHandler { responseInfo, stream ->
      HttpClientUtil.readSuccessResponseWithLogging(logger, request, responseInfo, stream) {
        deserializer.readAndTraverseGQLResponse(it, pathFromData, clazz)
      }
    }
    return httpHelper.sendAndAwaitCancellable(request, handler)
  }
}