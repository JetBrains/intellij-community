// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth.services

import com.intellij.collaboration.auth.credentials.Credentials
import com.intellij.ide.BrowserUtil
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

/**
 * The basic service that implements general authorization flow methods
 *
 * @param T Service credentials, must implement the Credentials interface
 */
abstract class OAuthServiceBase<T : Credentials> : OAuthService<T> {
  protected val currentRequest = AtomicReference<OAuthRequestWithResult<T>?>()

  override fun authorize(request: OAuthRequest<T>): CompletableFuture<T> {
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

  protected open fun startAuthorization(request: OAuthRequest<T>) {
    val authUrl = request.authUrlWithParameters.toExternalForm()
    BrowserUtil.browse(authUrl)
  }

  private fun OAuthRequestWithResult<T>.processCode(code: String) {
    try {
      when (val acquireResult = request.credentialsAcquirer.acquireCredentials(code)) {
        is OAuthCredentialsAcquirer.AcquireCredentialsResult.Success -> {
          result.complete(acquireResult.credentials)
        }
        is OAuthCredentialsAcquirer.AcquireCredentialsResult.Error -> {
          result.completeExceptionally(RuntimeException(acquireResult.description))
        }
      }
    }
    catch (e: Exception) {
      result.completeExceptionally(e)
    }
  }

  protected data class OAuthRequestWithResult<T : Credentials>(
    val request: OAuthRequest<T>,
    val result: CompletableFuture<T>
  )
}