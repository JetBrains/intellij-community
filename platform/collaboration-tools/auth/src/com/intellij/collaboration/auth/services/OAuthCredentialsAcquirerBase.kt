// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth.services

import com.intellij.collaboration.auth.credentials.Credentials
import com.intellij.util.Url
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse

abstract class OAuthCredentialsAcquirerBase<T : Credentials> : OAuthCredentialsAcquirer<T> {
  override fun acquireCredentials(code: String): OAuthCredentialsAcquirer.AcquireCredentialsResult<T> {
    val tokenUrl = getTokenUrlWithParameters(code).toExternalForm()
    val response = postHttpResponse(tokenUrl)
    return convertToAcquireCredentialsResult(response) { body, headers ->
      getCredentials(body, headers)
    }
  }

  abstract fun getTokenUrlWithParameters(code: String): Url

  abstract fun getCredentials(responseBody: String, responseHeaders: HttpHeaders): T

  companion object {
    fun postHttpResponse(url: String): HttpResponse<String> {
      val client = HttpClient.newHttpClient()
      val request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.noBody())
        .build()

      return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    fun <T : Credentials> convertToAcquireCredentialsResult(
      httpResponse: HttpResponse<String>,
      credentialsProvider: (body: String, headers: HttpHeaders) -> T
    ): OAuthCredentialsAcquirer.AcquireCredentialsResult<T> {
      return if (httpResponse.statusCode() == 200) {
        val creds = credentialsProvider(httpResponse.body(), httpResponse.headers())
        OAuthCredentialsAcquirer.AcquireCredentialsResult.Success(creds)
      }
      else {
        OAuthCredentialsAcquirer.AcquireCredentialsResult.Error(httpResponse.body().ifEmpty { "No token provided" })
      }
    }
  }
}