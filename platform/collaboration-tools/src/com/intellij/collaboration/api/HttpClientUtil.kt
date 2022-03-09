// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.text.nullize
import java.io.InputStream
import java.net.http.HttpHeaders
import java.net.http.HttpResponse
import java.util.zip.GZIPInputStream

/**
 * Base class to perform API requests via JDK11 http library
 * Assists with handling compression, sets proper timeouts, headers, etc.
 */
object HttpClientUtil {

  const val CONTENT_ENCODING_HEADER = "Content-Encoding"
  const val CONTENT_ENCODING_GZIP = "gzip"

  const val CONTENT_TYPE_HEADER = "Content-Type"
  const val CONTENT_TYPE_JSON = "application/json"

  private val LOG = thisLogger()

  inline fun <reified T> handleResponse(response: HttpResponse<InputStream>,
                                        crossinline bodyReader: (InputStream) -> T): T {
    checkResponse(response)
    return response.body().use { stream ->
      wrapGzipIfNeeded(response.headers(), stream).let(bodyReader)
    }
  }

  inline fun <reified T> handleOptionalResponse(response: HttpResponse<InputStream>,
                                                crossinline bodyReader: (InputStream) -> T): T? {
    try {
      checkResponse(response)
      return response.body().use { stream ->
        wrapGzipIfNeeded(response.headers(), stream).let(bodyReader)
      }
    }
    catch (e: HttpStatusErrorException) {
      if (e.statusCode == 404) return null
      throw e
    }
  }

  @PublishedApi
  internal fun checkResponse(response: HttpResponse<InputStream>) {
    val request = response.request()
    val statusCode = response.statusCode()
    if (statusCode < 400) {
      LOG.debug("Request: ${request.method()} ${request.uri()} : Success ${statusCode}")
      return
    }

    val errorText = response.body().reader().readText().nullize()
    LOG.debug("Request: ${request.method()} ${request.uri()} : Error ${statusCode} body:\n${errorText}")

    throw HttpStatusErrorException(request.method(), request.uri().toString(), statusCode, errorText)
  }

  @PublishedApi
  internal fun wrapGzipIfNeeded(headers: HttpHeaders, stream: InputStream): InputStream =
    if (headers.allValues(CONTENT_ENCODING_HEADER).contains(CONTENT_ENCODING_GZIP)) {
      GZIPInputStream(stream)
    }
    else stream
}