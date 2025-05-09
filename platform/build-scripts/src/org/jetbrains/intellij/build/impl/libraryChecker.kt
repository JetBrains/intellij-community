// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.platform.util.coroutines.mapConcurrent
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.LibraryLicense
import org.jetbrains.intellij.build.executeStep
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Collections

private val CONNECT_TIMEOUT = Duration.ofMillis(System.getProperty("idea.connection.timeout")?.toLong() ?: 30_000)
private val READ_TIMEOUT = Duration.ofMillis(System.getProperty("idea.read.timeout")?.toLong() ?: 60_000)

suspend fun checkLibraryUrls(context: BuildContext) {
  context.executeStep(spanBuilder("checking library URLs"), BuildOptions.LIBRARY_URL_CHECK_STEP) {
    val licenses = context.productProperties.allLibraryLicenses
    val errors = Collections.synchronizedList(ArrayList<String>())
    checkLicenseUrls(licenses, errors)
    checkWebsiteUrls(licenses, errors)
    if (errors.isNotEmpty()) {
      context.messages.error("Library URLs check failed. Errors:\n${errors.joinToString("\n")}")
    }
  }
}

private fun checkLicenseUrls(licenses: List<LibraryLicense>, errors: MutableList<String>) {
  val knownLicenses = setOf(
    "https://www.apache.org/licenses/LICENSE-2.0",
    "https://www.gnu.org/licenses/lgpl-3.0.html",
    "https://www.gnu.org/licenses/old-licenses/lgpl-2.1.en.html",
    "https://opensource.org/licenses/BSD-2-Clause",
    "https://opensource.org/licenses/BSD-3-Clause",
    "https://opensource.org/licenses/MIT",
  )
  val map = licenses.asSequence()
    .filterNot { it.licenseUrl == null || it.licenseUrl in knownLicenses }
    .groupBy { it.licenseUrl }
    .mapKeys { it.key!! }
  checkUrls("License", map, errors)
}

private fun checkWebsiteUrls(licenses: List<LibraryLicense>, errors: MutableList<String>) {
  val knownProblems = setOf(
    "https://github.com/JetBrains/intellij-deps-commons-vfs",  // private repo
    "https://www.dabeaz.com/ply/",  // JRE does not recognize the certificate
    "https://packages.jetbrains.team/maven/p/ki/maven",  // the Space Packages app is too picky
    "https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public/",
    "https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/rocksdbjni/",
  )
  val map = licenses.asSequence()
    .filterNot { it.url == null || it.url in knownProblems || (it.licenseUrl ?: "").startsWith(it.url) }
    .groupBy { it.url }
    .mapKeys { it.key!! }
  checkUrls("Website", map, errors)
}

private fun checkUrls(type: String, urls: Map<String, List<LibraryLicense>>, errors: MutableList<String>) {
  val maxParallelPerHosts = 4
  val span = Span.current()

  // to run parallel requests to different hosts, we need to group URLs by host
  val urlsAndLicensesGroupedByHost = urls.entries.groupBy { URL(it.key).host }

  val builder = Attributes.builder()
  builder.put("__TOTAL__", urls.size.toLong())
  urlsAndLicensesGroupedByHost.entries
    .map { it.key to it.value.size }
    .sortedByDescending { it.second }
    .forEach {
      builder.put(it.first, it.second.toLong())
    }
  span.addEvent("Number of ${type} URLs", builder.build())

  fun usedIn(libs: List<LibraryLicense>): String = "Used in: ${libs.joinToString { "'${it.presentableName}'" }}"

  val client = HttpClient.newBuilder()
    .executor(Dispatchers.IO.asExecutor())
    .connectTimeout(CONNECT_TIMEOUT)
    .followRedirects(HttpClient.Redirect.ALWAYS)
    .build()

  runBlocking(Dispatchers.IO) {
    for (group in urlsAndLicensesGroupedByHost.values) {
      launch {
        group.mapConcurrent(concurrency = maxParallelPerHosts) { (url, libraries) ->
          try {
            val request = HttpRequest.newBuilder(URI(url))
              .apply { if (url.startsWith("https://redocly.com/")) GET() else method("HEAD", HttpRequest.BodyPublishers.noBody()) }
              .timeout(READ_TIMEOUT)
              .header("User-Agent", "IntelliJ")
              .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != HttpURLConnection.HTTP_OK) {
              errors += "${type} URL '${url}' error: ${response.statusCode()}. ${usedIn(libraries)}"
            }
          }
          catch (e: Exception) {
            errors += "${type} URL '${url}': ${e.javaClass.name}: ${e.message}. ${usedIn(libraries)}"
          }
        }
      }
    }
  }
}
