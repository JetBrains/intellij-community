// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth.services

import com.intellij.collaboration.auth.credentials.Credentials
import com.intellij.ide.BrowserUtil
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
  protected val currentRequest = AtomicReference<OAuthRequestWithResult<T>?>()

  override fun authorize(request: OAuthRequest): CompletableFuture<T> {
    if (!currentRequest.compareAndSet(null, OAuthRequestWithResult(request, CompletableFuture<T>()))) {
      return currentRequest.get()!!.result
    }

    val result = currentRequest.get()!!.result
    result.whenComplete { _, _ -> currentRequest.set(null) }
    startAuthorization(request)

    return result
  }

  private fun Map<String, List<String>>.getAuthorizationCode(): String? = this["code"]?.firstOrNull()

  override fun handleServerCallback(path: String, parameters: Map<String, List<String>>): Boolean {
    val request = currentRequest.get() ?: return false

    if (path != request.request.authorizationCodeUrl.path) {
      return false
    }
    val code = parameters.getAuthorizationCode() ?: return false

    request.processCode(code)
    val result = request.result
    return result.isDone && !result.isCancelled && !result.isCompletedExceptionally
  }

  private fun startAuthorization(request: OAuthRequest) {
    val authUrl = request.getAuthUrlWithParameters().toExternalForm()
    BrowserUtil.browse(authUrl)
  }

  private fun OAuthRequestWithResult<T>.processCode(code: String) {
    try {
      val tokenUrl = request.getTokenUrlWithParameters(code).toExternalForm()
      val response = postHttpResponse(buildHttpRequest(tokenUrl, code))

      if (response.statusCode() == 200) {
        val creds = getCredentials(response.body(), response.headers())
        result.complete(creds)
      }
      else {
        result.completeExceptionally(RuntimeException(response.body().ifEmpty { "No token provided" }))
      }
    }
    catch (e: IOException) {
      result.completeExceptionally(e)
    }
  }

  protected open fun buildHttpRequest(url: String, code: String? = null): HttpRequest {
    return HttpRequest.newBuilder()
      .uri(URI.create(url))
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.noBody())
      .build()
  }

  protected fun postHttpResponse(request: HttpRequest): HttpResponse<String> {
    val client = HttpClient.newHttpClient()

    return client.send(request, HttpResponse.BodyHandlers.ofString())
  }

  protected abstract fun getCredentials(responseBody: String, responseHeaders: HttpHeaders): T

  protected data class OAuthRequestWithResult<T : Credentials>(
    val request: OAuthRequest,
    val result: CompletableFuture<T>
  )
}