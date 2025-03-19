// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.json

import com.intellij.collaboration.api.HttpApiHelper
import com.intellij.collaboration.api.httpclient.ByteArrayProducingBodyPublisher
import com.intellij.collaboration.api.httpclient.HttpClientUtil
import com.intellij.collaboration.api.httpclient.HttpClientUtil.inflateAndReadWithErrorHandlingAndLogging
import com.intellij.collaboration.api.logName
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.annotations.ApiStatus
import java.io.Reader
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@ApiStatus.Experimental
interface JsonHttpApiHelper {
  fun jsonBodyPublisher(uri: URI, body: Any): HttpRequest.BodyPublisher
  suspend fun <T> loadJsonValueByClass(request: HttpRequest, clazz: Class<T>): HttpResponse<out T>
  suspend fun <T> loadOptionalJsonValueByClass(request: HttpRequest, clazz: Class<T>): HttpResponse<out T?>
  suspend fun <T> loadJsonListByClass(request: HttpRequest, clazz: Class<T>): HttpResponse<out List<T>>
  suspend fun <T> loadOptionalJsonListByClass(request: HttpRequest, clazz: Class<T>): HttpResponse<out List<T>?>

  fun HttpRequest.Builder.withJsonContent(): HttpRequest.Builder = apply {
    header(HttpClientUtil.CONTENT_TYPE_HEADER, HttpClientUtil.CONTENT_TYPE_JSON)
  }
}

@ApiStatus.Experimental
suspend inline fun <reified T> JsonHttpApiHelper.loadJsonValue(request: HttpRequest): HttpResponse<out T> =
  loadJsonValueByClass(request, T::class.java)

@ApiStatus.Experimental
suspend inline fun <reified T> JsonHttpApiHelper.loadOptionalJsonValue(request: HttpRequest): HttpResponse<out T?> =
  loadOptionalJsonValueByClass(request, T::class.java)

@ApiStatus.Experimental
suspend inline fun <reified T> JsonHttpApiHelper.loadJsonList(request: HttpRequest): HttpResponse<out List<T>> =
  loadJsonListByClass(request, T::class.java)

@ApiStatus.Experimental
suspend inline fun <reified T> JsonHttpApiHelper.loadOptionalJsonList(request: HttpRequest): HttpResponse<out List<T>?> =
  loadOptionalJsonListByClass(request, T::class.java)


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
  : JsonHttpApiHelper, HttpApiHelper by httpHelper {
  /**
   * Performs the given HTTP request and processes the response. The response body is inflated if necessary,
   * status code is checked to be OK, the body is passed to the [load] and [map] functions, and errors are
   * logged and rethrown when they occur in the [load] function.
   */
  private suspend inline fun <T, R> loadWithMapperAndLogErrors(
    request: HttpRequest,
    crossinline map: (T?) -> R,
    crossinline load: (Reader) -> T?
  ): HttpResponse<out R> {
    val bodyHandler = inflateAndReadWithErrorHandlingAndLogging(logger, request) { reader, _ ->
      val result = try {
        load(reader)
      }
      catch (e: Throwable) {
        logger.warn("API response deserialization failed", e)
        throw HttpJsonDeserializationException(request.logName(), e)
      }

      map(result)
    }
    return httpHelper.sendAndAwaitCancellable(request, bodyHandler)
  }

  override fun jsonBodyPublisher(uri: URI, body: Any): HttpRequest.BodyPublisher {
    return ByteArrayProducingBodyPublisher {
      val jsonBytes = serializer.toJsonBytes(body)
      if (logger.isTraceEnabled) {
        logger.trace("Request POST $uri : Request body: " + String(jsonBytes, Charsets.UTF_8))
      }
      jsonBytes
    }
  }

  override suspend fun <T> loadJsonValueByClass(request: HttpRequest, clazz: Class<T>): HttpResponse<out T> =
    loadWithMapperAndLogErrors(request, { it ?: error("Empty response") }) { reader -> deserializer.fromJson(reader, clazz) }

  override suspend fun <T> loadOptionalJsonValueByClass(request: HttpRequest, clazz: Class<T>): HttpResponse<out T?> =
    loadWithMapperAndLogErrors(request, { it }) { reader -> deserializer.fromJson(reader, clazz) }

  @Suppress("UNCHECKED_CAST")
  override suspend fun <T> loadJsonListByClass(request: HttpRequest, clazz: Class<T>): HttpResponse<out List<T>> =
    loadWithMapperAndLogErrors(request, { it ?: error("Empty response") }) { reader -> (deserializer.fromJson(reader, List::class.java, clazz) as? List<T>) }

  @Suppress("UNCHECKED_CAST")
  override suspend fun <T> loadOptionalJsonListByClass(request: HttpRequest, clazz: Class<T>): HttpResponse<out List<T>?> =
    loadWithMapperAndLogErrors(request, { it }) { reader -> (deserializer.fromJson(reader, List::class.java, clazz) as? List<T>) }
}
