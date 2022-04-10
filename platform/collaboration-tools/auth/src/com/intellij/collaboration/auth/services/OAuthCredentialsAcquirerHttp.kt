// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth.services

import com.intellij.collaboration.auth.credentials.Credentials
import com.intellij.util.Url
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse

object OAuthCredentialsAcquirerHttp {
  fun <T : Credentials> requestToken(
    url: Url,
    credentialsProvider: (body: String, headers: HttpHeaders) -> T
  ): OAuthCredentialsAcquirer.AcquireCredentialsResult<T> {
    val response = try {
      requestToken(url)
    }
    catch (e: IOException) {
      return OAuthCredentialsAcquirer.AcquireCredentialsResult.Error("Cannot exchange token: ${e.message}")
    }
    return convertToAcquireCredentialsResult(response) { body, headers ->
      credentialsProvider(body, headers)
    }
  }

  fun requestToken(url: Url): HttpResponse<String> {
    val tokenUrl = url.toExternalForm()
    val client = HttpClient.newBuilder()
      .version(HttpClient.Version.HTTP_1_1)
      .build()
    val request = HttpRequest.newBuilder()
      .uri(URI.create(tokenUrl))
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