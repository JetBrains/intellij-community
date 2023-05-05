// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.json

import com.intellij.collaboration.api.HttpApiHelper
import com.intellij.collaboration.api.httpclient.ByteArrayProducingBodyPublisher
import com.intellij.collaboration.api.httpclient.HttpClientUtil
import com.intellij.collaboration.api.httpclient.HttpClientUtil.readSuccessResponseWithLogging
import com.intellij.collaboration.api.httpclient.InflatedStreamReadingBodyHandler
import com.intellij.collaboration.api.logName
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.annotations.ApiStatus
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@ApiStatus.Experimental
interface JsonHttpApiHelper {
  fun jsonBodyPublisher(uri: URI, body: Any): HttpRequest.BodyPublisher
  suspend fun <T> loadJsonValue(request: HttpRequest, clazz: Class<T>): HttpResponse<out T>
  suspend fun <T> loadOptionalJsonValue(request: HttpRequest, clazz: Class<T>): HttpResponse<out T?>
  suspend fun <T> loadJsonList(request: HttpRequest, clazz: Class<T>): HttpResponse<out List<T>>
  suspend fun <T> loadOptionalJsonList(request: HttpRequest, clazz: Class<T>): HttpResponse<out List<T>?>

  fun HttpRequest.Builder.withJsonContent(): HttpRequest.Builder = apply {
    header(HttpClientUtil.CONTENT_TYPE_HEADER, HttpClientUtil.CONTENT_TYPE_JSON)
  }
}

@ApiStatus.Experimental
suspend inline fun <reified T> JsonHttpApiHelper.loadJsonValue(request: HttpRequest): HttpResponse<out T> =
  loadJsonValue(request, T::class.java)

@ApiStatus.Experimental
suspend inline fun <reified T> JsonHttpApiHelper.loadOptionalJsonValue(request: HttpRequest): HttpResponse<out T?> =
  loadOptionalJsonValue(request, T::class.java)

@ApiStatus.Experimental
suspend inline fun <reified T> JsonHttpApiHelper.loadJsonList(request: HttpRequest): HttpResponse<out List<T>> =
  loadJsonList(request, T::class.java)

@ApiStatus.Experimental
suspend inline fun <reified T> JsonHttpApiHelper.loadOptionalJsonList(request: HttpRequest): HttpResponse<out List<T>?> =
  loadOptionalJsonList(request, T::class.java)


@ApiStatus.Experimental
fun JsonHttpApiHelper(
  logger: Logger,
  httpHelper: HttpApiHelper,
  serializer: JsonDataSerializer,
  deserializer: JsonDataDeserializer
): JsonHttpApiHelper =
  JsonHttpApiHelperImpl(logger, httpHelper, serializer, deserializer)

private class JsonHttpApiHelperImpl(
  private val logger: Logger,
  private val httpHelper: HttpApiHelper,
  private val serializer: JsonDataSerializer,
  private val deserializer: JsonDataDeserializer)
  : JsonHttpApiHelper {

  override fun jsonBodyPublisher(uri: URI, body: Any): HttpRequest.BodyPublisher {
    return ByteArrayProducingBodyPublisher {
      val jsonBytes = serializer.toJsonBytes(body)
      if (logger.isTraceEnabled) {
        logger.trace("Request POST $uri : Request body: " + String(jsonBytes, Charsets.UTF_8))
      }
      jsonBytes
    }
  }

  override suspend fun <T> loadJsonValue(request: HttpRequest, clazz: Class<T>): HttpResponse<out T> {
    val bodyHandler = InflatedStreamReadingBodyHandler { responseInfo, stream ->
      readSuccessResponseWithLogging(logger, request, responseInfo, stream) {
        try {
          deserializer.fromJson(it, clazz) ?: error("Empty response")
        }
        catch (e: Throwable) {
          throw HttpJsonDeserializationException(request.logName(), e)
        }
      }
    }
    return httpHelper.sendAndAwaitCancellable(request, bodyHandler)
  }

  override suspend fun <T> loadOptionalJsonValue(request: HttpRequest, clazz: Class<T>): HttpResponse<out T?> {
    val bodyHandler = InflatedStreamReadingBodyHandler { responseInfo, stream ->
      readSuccessResponseWithLogging(logger, request, responseInfo, stream) {
        try {
          deserializer.fromJson(it, clazz)
        }
        catch (e: Throwable) {
          throw HttpJsonDeserializationException(request.logName(), e)
        }
      }
    }
    return httpHelper.sendAndAwaitCancellable(request, bodyHandler)
  }

  override suspend fun <T> loadJsonList(request: HttpRequest, clazz: Class<T>): HttpResponse<out List<T>> {
    val bodyHandler = InflatedStreamReadingBodyHandler { responseInfo, stream ->
      readSuccessResponseWithLogging(logger, request, responseInfo, stream) {
        try {
          @Suppress("UNCHECKED_CAST")
          (deserializer.fromJson(it, List::class.java, clazz) as? List<T>) ?: error("Empty response")
        }
        catch (e: Throwable) {
          throw HttpJsonDeserializationException(request.logName(), e)
        }
      }
    }
    return httpHelper.sendAndAwaitCancellable(request, bodyHandler)
  }

  override suspend fun <T> loadOptionalJsonList(request: HttpRequest, clazz: Class<T>): HttpResponse<out List<T>?> {
    val bodyHandler = InflatedStreamReadingBodyHandler { responseInfo, stream ->
      readSuccessResponseWithLogging(logger, request, responseInfo, stream) {
        try {
          @Suppress("UNCHECKED_CAST")
          deserializer.fromJson(it, List::class.java, clazz) as? List<T>
        }
        catch (e: Throwable) {
          throw HttpJsonDeserializationException(request.logName(), e)
        }
      }
    }
    return httpHelper.sendAndAwaitCancellable(request, bodyHandler)
  }
}
