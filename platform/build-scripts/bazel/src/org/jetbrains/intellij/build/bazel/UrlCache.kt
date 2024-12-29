// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.bazel

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.*
import kotlin.io.path.isRegularFile
import kotlin.io.path.moveTo

@Serializable
internal data class CacheEntry(
  @JvmField val path: String,
  @JvmField val url: String,
  @JvmField val sha256: String,
  @Transient @JvmField var used: Boolean = false,
)

internal data class JarRepository(@JvmField val url: String, @JvmField val isPrivate: Boolean)

private val authHeaderValue by lazy {
  val netrcPath = Paths.get(System.getProperty("user.home"), ".netrc")
  require(netrcPath.isRegularFile()) {
    ".netrc not found - please configure auth to access private repositories"
  }
  val machine = "packages.jetbrains.team"
  val credentials = parseNetrc(netrcPath, machine)
  require(credentials != null) {
    "No credentials found for machine: $machine"
  }

  "Basic " + Base64.getEncoder().encodeToString("${credentials.first}:${credentials.second}".toByteArray())
}

internal class UrlCache(private val cacheFile: Path) {
  private val httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()

  private val cache: MutableMap<String, CacheEntry> by lazy {
    if (Files.exists(cacheFile)) {
      Json.decodeFromString<List<CacheEntry>>(Files.readString(cacheFile)).associateTo(HashMap()) { it.path to it }
    }
    else {
      HashMap<String, CacheEntry>()
    }
  }

  @OptIn(ExperimentalSerializationApi::class)
  fun save() {
    val entries = cache.values.filter { it.used }.toTypedArray()
    entries.sortBy { it.path }
    val tempFile = Files.createTempFile(cacheFile.fileName.toString(), ".tmp")
    @Suppress("JSON_FORMAT_REDUNDANT")
    Files.writeString(tempFile, Json {
      prettyPrint = true
      prettyPrintIndent = "  "
    }.encodeToString(entries))
    tempFile.moveTo(target = cacheFile, overwrite = true)
  }

  fun getEntry(jarPath: String): CacheEntry? = cache.get(jarPath)?.also { it.used = true }

  fun putUrl(jarPath: String, url: String, repo: JarRepository): CacheEntry {
    val hash = calculateHash(url, repo)
    val entry = CacheEntry(path = jarPath, url = url, sha256 = hash, used = true)
    cache.put(jarPath, entry)
    return entry
  }

  fun checkUrl(url: String, repo: JarRepository): Boolean {
    val requestBuilder = HttpRequest.newBuilder()
      .uri(URI.create(url))
      .method("HEAD", HttpRequest.BodyPublishers.noBody())
    addAuthIfNeeded(repo, requestBuilder)

    val response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.discarding())
    val statusCode = response?.statusCode()
    if (statusCode == 401) {
      throw IllegalStateException("Not authorized: $url")
    }
    return statusCode == 200
  }

  fun calculateHash(url: String, repo: JarRepository): String {
    val digest = MessageDigest.getInstance("SHA-256")
    println("Downloading '$url'")
    val requestBuilder = HttpRequest.newBuilder()
      .uri(URI.create(url))
      .GET()
    addAuthIfNeeded(repo, requestBuilder)
    val response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream())
    response.body().use { inputStream ->
      val buffer = ByteArray(8192)
      var bytesRead: Int
      while (inputStream.read(buffer).also { bytesRead = it } != -1) {
        digest.update(buffer, 0, bytesRead)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }
}

private fun addAuthIfNeeded(url: JarRepository, requestBuilder: HttpRequest.Builder) {
  if (url.isPrivate) {
    requestBuilder.header("Authorization", authHeaderValue)
  }
}

private fun parseNetrc(netrcPath: Path, @Suppress("SameParameterValue") machine: String): Pair<String, String>? {
  val content = Files.readString(netrcPath)
  val pattern = Regex("""machine\s+$machine\s+login\s+(\S+)\s+password\s+(\S+)""")
  val result = pattern.find(content)
  if (result != null) {
    return Pair(result.groupValues.get(1), result.groupValues.get(2))
  }
  // no matching machine found
  return null
}