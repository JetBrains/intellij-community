// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.auth

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.util.io.await
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.net.MalformedURLException
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.optionals.getOrNull

private val spacePackagePathRegex = Regex(".*/p/(?<project>[\\w-]+)/(?<repository>[\\w-]+)")

// used by Qodana and Space plugins
@ApiStatus.Internal
suspend fun isSpacePrivatePackageUrl(url: String): Boolean {
  if (!isSpacePackageUrl(url)) {
    // don't do request if fast check returned false
    return false
  }
  return serviceAsync<SpacePackagesCheckerService>().isSpacePrivatePackageUrl(url)
}

@ApiStatus.Internal
fun isSpacePackageUrl(url: String): Boolean {
  return try {
    matchSpacePackageUrlRegex(url) != null
  }
  catch (_: MalformedURLException) {
    false
  }
}

@ApiStatus.Internal
fun matchSpacePackageUrlRegex(url: String): MatchResult? {
  return spacePackagePathRegex.matchEntire(URL(url).pathWithSlash)
}

/**
 * Provides a way to check urls that they are Space private Packages
 *
 * This service allows caching of check results and reuse single [HttpClient] for all the requests
 */
@Service(Service.Level.APP)
private class SpacePackagesCheckerService(private val scope: CoroutineScope) {
  private val client: HttpClient by lazy {
    HttpClient.newBuilder()
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build()
  }

  private val checkedUrls = ConcurrentHashMap<String, Deferred<Boolean>>()

  suspend fun isSpacePrivatePackageUrl(url: String): Boolean {
    val isPrivatePackageDeferred = scope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
      isSpacePrivatePackageRequest(url)
    }

    val alreadyComputing = checkedUrls.putIfAbsent(url, isPrivatePackageDeferred)
    if (alreadyComputing != null) {
      // if another coroutine computes result for [url]
      // we can cancel our request (it is not started because of CoroutineStart.LAZY)
      isPrivatePackageDeferred.cancel()
      return try {
        alreadyComputing.await()
      }
      catch (e: Exception) {
        // clear cache, so next requests can compute it again
        checkedUrls.remove(url, alreadyComputing)
        throw e
      }
    }

    return isPrivatePackageDeferred.await()
  }

  private suspend fun isSpacePrivatePackageRequest(url: String): Boolean {
    val response = withContext(Dispatchers.IO) {
      client.sendAsync(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString()).await()
    }
    val serverHeader = response.headers().firstValue("server").getOrNull() ?: return false
    if (!serverHeader.contains("Space Packages")) {
      return false
    }
    val authenticateHeader = response.headers().firstValue("www-authenticate")
    return authenticateHeader.isPresent
  }
}

/**
 * It is used in regexp to check that `p` is not the last letter in a word but a complete path segment
 */
private val URL.pathWithSlash
  get() = "/" + this.path.trim('/')