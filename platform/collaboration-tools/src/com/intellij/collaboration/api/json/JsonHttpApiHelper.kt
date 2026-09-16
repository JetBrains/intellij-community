// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.json

import com.intellij.collaboration.api.EmptyHttpResponseException
import com.intellij.collaboration.api.HttpApiHelper
import com.intellij.collaboration.api.httpclient.HttpClientUtil
import com.intellij.collaboration.api.httpclient.ensureAcceptHeader
import com.intellij.collaboration.api.logName
import com.intellij.collaboration.api.sendAndRead
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.Charset

@ApiStatus.Experimental
interface JsonHttpApiHelper {
  @Throws(HttpJsonSerializationException::class)
  suspend fun putJson(uri: URI, body: Any): HttpRequest.Builder

  @Throws(HttpJsonSerializationException::class)
  suspend fun postJson(uri: URI, body: Any): HttpRequest.Builder

  @Throws(HttpJsonSerializationException::class)
  suspend fun sendJson(uri: URI, httpMethod: String, body: Any): HttpRequest.Builder

  @Throws(HttpJsonSerializationException::class)
  suspend fun jsonBodyPublisher(httpMethod: String, uri: URI, body: Any): HttpRequest.BodyPublisher

  fun HttpRequest.Builder.withJsonContent(): HttpRequest.Builder

  @Throws(IOException::class, HttpJsonDeserializationException::class, EmptyHttpResponseException::class)
  suspend fun <T : Any> loadJsonValueByClass(request: HttpRequest, clazz: Class<T>): HttpResponse<out T>

  @Throws(IOException::class, HttpJsonDeserializationException::class)
  suspend fun <T : Any> loadOptionalJsonValueByClass(request: HttpRequest, clazz: Class<T>): HttpResponse<out T?>

  @Throws(IOException::class, HttpJsonDeserializationException::class, EmptyHttpResponseException::class)
  suspend fun <T : Any> loadJsonListByClass(request: HttpRequest, clazz: Class<T>): HttpResponse<out List<T>>

  @Throws(IOException::class, HttpJsonDeserializationException::class)
  suspend fun <T : Any> loadOptionalJsonListByClass(request: HttpRequest, clazz: Class<T>): HttpResponse<out List<T>?>
}

@ApiStatus.Experimental
@Throws(IOException::class, HttpJsonDeserializationException::class)
context(api: JsonHttpApiHelper)
suspend inline fun <reified T : Any> HttpRequest.loadJsonValue(): HttpResponse<out T> = api.loadJsonValueByClass(this, T::class.java)

@ApiStatus.Experimental
@Throws(IOException::class, HttpJsonDeserializationException::class, EmptyHttpResponseException::class)
context(api: JsonHttpApiHelper)
suspend inline fun <reified T : Any> HttpRequest.loadOptionalJsonValue(): HttpResponse<out T?> =
  api.loadOptionalJsonValueByClass(this, T::class.java)

@ApiStatus.Experimental
@Throws(IOException::class, HttpJsonDeserializationException::class)
context(api: JsonHttpApiHelper)
suspend inline fun <reified T : Any> HttpRequest.loadJsonList(): HttpResponse<out List<T>> =
  api.loadJsonListByClass(this, T::class.java)

@ApiStatus.Experimental
@Throws(IOException::class, HttpJsonDeserializationException::class, EmptyHttpResponseException::class)
context(api: JsonHttpApiHelper)
suspend inline fun <reified T : Any> HttpRequest.loadOptionalJsonList(): HttpResponse<out List<T>?> =
  api.loadOptionalJsonListByClass(this, T::class.java)


@ApiStatus.Experimental
fun JsonHttpApiHelper(
  logger: Logger,
  httpHelper: HttpApiHelper,
  serializer: JsonDataSerializer,
  deserializer: JsonDataDeserializer,
): JsonHttpApiHelper =
  JsonHttpApiHelperImpl(logger, httpHelper,
                        serializer, HttpClientUtil.MIME_TYPE_JSON,
                        deserializer, HttpClientUtil.MIME_TYPE_JSON)

private class JsonHttpApiHelperImpl(
  private val logger: Logger,
  private val httpHelper: HttpApiHelper,
  private val serializer: JsonDataSerializer,
  private val defaultSendContentType: String,
  private val deserializer: JsonDataDeserializer,
  private val defaultAcceptMimeType: String,
) : JsonHttpApiHelper, HttpApiHelper by httpHelper {
  override suspend fun putJson(uri: URI, body: Any): HttpRequest.Builder =
    request(uri).PUT(jsonBodyPublisher("PUT", uri, body)).withJsonContent()

  override suspend fun postJson(uri: URI, body: Any): HttpRequest.Builder =
    request(uri).POST(jsonBodyPublisher("POST", uri, body)).withJsonContent()

  override suspend fun sendJson(uri: URI, httpMethod: String, body: Any): HttpRequest.Builder =
    request(uri).method(httpMethod, jsonBodyPublisher(httpMethod, uri, body)).withJsonContent()

  override fun HttpRequest.Builder.withJsonContent(): HttpRequest.Builder =
    header(HttpClientUtil.CONTENT_TYPE_HEADER, defaultSendContentType)

  private suspend inline fun <T> HttpRequest.sendAndRead(
    noinline bodyReader: InputStream.(requestName: String, mimeType: String?, charset: Charset?) -> T,
  ): HttpResponse<T> {
    val originalRequest = this
    val request = originalRequest.ensureAcceptHeader(defaultAcceptMimeType)
    val requestName = request.logName()
    return sendAndRead(request, requestName) { mimeType, charset ->
      bodyReader(requestName, mimeType, charset)
    }
  }

  override suspend fun jsonBodyPublisher(httpMethod: String, uri: URI, body: Any): HttpRequest.BodyPublisher {
    val requestName = HttpClientUtil.getRequestName(httpMethod, uri)
    val jsonBytes = withContext(Dispatchers.Default) {
      try {
        serializer.toJsonBytes(body)
      }
      catch (e: Exception) {
        logger.warn("API request serialization failed", e)
        throw HttpJsonSerializationException(requestName, e)
      }
    }
    if (logger.isTraceEnabled) {
      logger.trace("Request $httpMethod : Request body: " + String(jsonBytes, Charsets.UTF_8))
    }
    return HttpRequest.BodyPublishers.ofByteArray(jsonBytes)
  }

  private inline fun <T : Any> InputStream.deserialize(
    requestName: String,
    charset: Charset?,
    deserializer: (InputStream, Charset) -> T?,
  ): T? {
    try {
      return deserializer(this, charset ?: Charsets.UTF_8)
    }
    catch (e: Exception) {
      logger.warn("API response deserialization failed", e)
      throw HttpJsonDeserializationException(requestName, e)
    }
  }

  override suspend fun <T : Any> loadJsonValueByClass(request: HttpRequest, clazz: Class<T>): HttpResponse<out T> =
    request.sendAndRead { requestName, mimeType, charset ->
      checkJsonMimeType(mimeType)
      deserialize(requestName, charset) { stream, charset ->
        deserializer.readJson(stream, charset, clazz)
      } ?: throw EmptyHttpResponseException(requestName)
    }

  override suspend fun <T : Any> loadOptionalJsonValueByClass(request: HttpRequest, clazz: Class<T>): HttpResponse<out T?> =
    request.sendAndRead { requestName, mimeType, charset ->
      if (mimeType == null || !HttpClientUtil.isJsonMimeType(mimeType)) {
        logger.debug("Request $requestName : no content type - inferring no content")
        return@sendAndRead null
      }
      deserialize(requestName, charset) { stream, charset ->
        deserializer.readJson(stream, charset, clazz)
      }
    }

  override suspend fun <T : Any> loadJsonListByClass(request: HttpRequest, clazz: Class<T>): HttpResponse<out List<T>> =
    request.sendAndRead { requestName, mimeType, charset ->
      checkJsonMimeType(mimeType)
      deserialize(requestName, charset) { stream, charset ->
        @Suppress("UNCHECKED_CAST")
        deserializer.readJson(stream, charset, List::class.java, clazz) as? List<T>
      } ?: throw EmptyHttpResponseException(requestName)
    }

  @Suppress("UNCHECKED_CAST")
  override suspend fun <T : Any> loadOptionalJsonListByClass(request: HttpRequest, clazz: Class<T>): HttpResponse<out List<T>?> =
    request.sendAndRead { requestName, mimeType, charset ->
      if (mimeType == null || !HttpClientUtil.isJsonMimeType(mimeType)) {
        logger.debug("Request $requestName : no content type - inferring no content")
        return@sendAndRead null
      }
      deserialize(requestName, charset) { stream, charset ->
        @Suppress("UNCHECKED_CAST")
        deserializer.readJson(stream, charset, List::class.java, clazz) as? List<T>
      }
    }
}

private fun checkJsonMimeType(mimeType: String?) {
  check(mimeType != null && HttpClientUtil.isJsonMimeType(mimeType)) { "JSON mime type expected, got $mimeType" }
}
