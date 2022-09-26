// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.graphql

import com.intellij.collaboration.api.HttpApiClient
import com.intellij.collaboration.api.dto.GraphQLRequestDTO
import com.intellij.collaboration.api.httpclient.ByteArrayProducingBodyPublisher
import com.intellij.collaboration.api.httpclient.HttpClientUtil
import com.intellij.collaboration.api.httpclient.StreamReadingBodyHandler
import java.io.InputStream
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse

abstract class GraphQLApiClient : HttpApiClient() {

  abstract val gqlQueryLoader: CachingGraphQLQueryLoader
  abstract val gqlSerializer: GraphQLDataSerializer

  fun gqlQuery(uri: URI, queryPath: String, variablesObject: Any? = null): HttpRequest {
    val publisher = object : ByteArrayProducingBodyPublisher() {
      override fun produceBytes(): ByteArray {
        logger.debug("Request POST $uri")
        val query = gqlQueryLoader.loadQuery(queryPath)
        val request = GraphQLRequestDTO(query, variablesObject)
        if (logger.isTraceEnabled) {
          logger.trace("Request POST $uri : Request body: " + gqlSerializer.toJson(request))
        }
        return gqlSerializer.toJsonBytes(request)
      }
    }

    return request(uri)
      .POST(publisher)
      .header(HttpClientUtil.CONTENT_TYPE_HEADER, HttpClientUtil.CONTENT_TYPE_JSON)
      .build()
  }

  suspend fun <T> loadGQLResponse(request: HttpRequest, clazz: Class<T>, vararg pathFromData: String): HttpResponse<T?> {
    return sendAndAwaitCancellable(request, gqlBodyHandler(request, pathFromData, clazz))
  }

  suspend inline fun <reified T> loadGQLResponse(request: HttpRequest, vararg pathFromData: String)
    : HttpResponse<T?> = loadGQLResponse(request, T::class.java, *pathFromData)

  private fun <T> gqlBodyHandler(request: HttpRequest, pathFromData: Array<out String>, clazz: Class<T>): HttpResponse.BodyHandler<T?> =
    object : StreamReadingBodyHandler<T?>(request) {

      override fun read(bodyStream: InputStream): T? {
        logger.debug("${request.logName()} : Success")

        if (logger.isTraceEnabled) {
          val body = bodyStream.reader().readText()
          logger.trace("${request.logName()} : Response body: $body")
          return gqlSerializer.readAndTraverseGQLResponse(body, pathFromData, clazz)
        }

        return gqlSerializer.readAndTraverseGQLResponse(bodyStream, pathFromData, clazz)
      }

      override fun handleError(statusCode: Int, errorBody: String): Nothing {
        logger.debug("${request.logName()} : Error ${statusCode}")
        if (logger.isTraceEnabled) {
          logger.trace("${request.logName()} : Response body: $errorBody")
        }
        super.handleError(statusCode, errorBody)
      }
    }
}
