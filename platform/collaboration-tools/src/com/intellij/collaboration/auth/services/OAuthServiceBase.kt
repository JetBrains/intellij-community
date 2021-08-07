// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth.services

import com.intellij.collaboration.auth.credentials.Credentials
import com.intellij.ide.BrowserUtil
import com.intellij.util.Url
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

/**
 * The basic service that implements general authorization flow methods
 *
 * @param T Service credentials, must implement the Credentials interface
 */
abstract class OAuthServiceBase<T : Credentials> : OAuthService<T> {
  protected val currentRequest = AtomicReference<CompletableFuture<T>?>()

  override fun authorize(): CompletableFuture<T> {
    if (!currentRequest.compareAndSet(null, CompletableFuture<T>())) {
      return currentRequest.get()!!
    }

    val request = currentRequest.get()!!
    request.whenComplete { _, _ -> currentRequest.set(null)  }
    startAuthorization()

    return request
  }

  override fun acceptCode(code: String): Boolean {
    val request = currentRequest.get() ?: return false

    request.processCode(code)
    return request.isDone && !request.isCancelled && !request.isCompletedExceptionally
  }

  private fun startAuthorization() {
    val authUrl = getAuthUrlWithParameters().toExternalForm()
    BrowserUtil.browse(authUrl)
  }

  private fun CompletableFuture<T>.processCode(code: String) {
    try {
      val tokenUrl = getTokenUrlWithParameters(code).toExternalForm()
      val response = postHttpResponse(tokenUrl)

      if (response.statusCode() == 200) {
        val result = getCredentials(response.body(), response.headers())
        complete(result)
      }
      else {
        completeExceptionally(RuntimeException(response.body().ifEmpty { "No token provided" }))
      }
    }
    catch (e: IOException) {
      completeExceptionally(e)
    }
  }

  protected fun postHttpResponse(url: String): HttpResponse<String> {
    val client = HttpClient.newHttpClient()
    val request: HttpRequest = HttpRequest.newBuilder()
      .uri(URI.create(url))
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.noBody())
      .build()

    return client.send(request, HttpResponse.BodyHandlers.ofString())
  }

  protected abstract fun getAuthUrlWithParameters(): Url

  protected abstract fun getTokenUrlWithParameters(code: String): Url

  protected abstract fun getCredentials(responseBody: String, responseHeaders: HttpHeaders): T
}