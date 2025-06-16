// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.bazel

import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.containers.orNull
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jdom.Namespace
import org.jetbrains.intellij.build.dependencies.BuildDependenciesConstants
import org.jetbrains.intellij.build.dependencies.TeamCityHelper
import java.io.InputStream
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

private fun getAuthFromSystemProperties(): Pair<String, String>? {
  val username = System.getProperty(BuildDependenciesConstants.JPS_AUTH_SPACE_USERNAME)
                 ?: TeamCityHelper.allProperties[BuildDependenciesConstants.JPS_AUTH_SPACE_USERNAME]
  val password = System.getProperty(BuildDependenciesConstants.JPS_AUTH_SPACE_PASSWORD)
                 ?: TeamCityHelper.allProperties[BuildDependenciesConstants.JPS_AUTH_SPACE_PASSWORD]
  if (username.isNullOrBlank() || password.isNullOrBlank()) {
    println("DEBUG: ${BuildDependenciesConstants.JPS_AUTH_SPACE_USERNAME} or ${BuildDependenciesConstants.JPS_AUTH_SPACE_PASSWORD} is empty in system properties - skipping auth from system properties")
    return null
  }

  println("DEBUG: got authentication from ${BuildDependenciesConstants.JPS_AUTH_SPACE_USERNAME} and ${BuildDependenciesConstants.JPS_AUTH_SPACE_PASSWORD}")
  return username to password
}

private fun getAuthFromMavenSettingsXml(): Pair<String, String>? {
  val settingsXmlFile = Paths.get(System.getProperty("user.home"), ".m2/settings.xml")
  if (!settingsXmlFile.isRegularFile()) {
    println("DEBUG: settings.xml not found at $settingsXmlFile - skipping auth from maven settings.xml")
    return null
  }

  @Suppress("HttpUrlsUsage")
  val mavenNamespace = Namespace.getNamespace("http://maven.apache.org/SETTINGS/1.0.0")

  val root = JDOMUtil.load(settingsXmlFile)
  val servers = root.getChild("servers", mavenNamespace) ?: run {
    println("DEBUG: no <servers> in ${settingsXmlFile} - skipping auth from maven settings.xml")
    return null
  }

  for (element in servers.getChildren("server", mavenNamespace)) {
    if (element.getChildTextTrim("id", mavenNamespace) != "intellij-private-dependencies") {
      continue
    }

    val username: String? = element.getChildTextTrim("username", mavenNamespace)
    val password: String? = element.getChildTextTrim("password", mavenNamespace)
    if (username.isNullOrBlank() || password.isNullOrBlank()) {
      println("DEBUG: username or password is empty in ${settingsXmlFile} for section <id>intellij-private-dependencies</id> - skipping auth from maven settings.xml")
      continue
    }

    println("DEBUG: got authentication from <id>intellij-private-dependencies</id> of $settingsXmlFile")
    return username to password
  }

  println("DEBUG: no section <id>intellij-private-dependencies</id> in ${settingsXmlFile} - skipping auth from maven settings.xml")
  return null
}

private fun getAuthFromNetrc(): Pair<String, String>? {
  val netrcPath = System.getenv("NETRC")?.let(Path::of) ?: Paths.get(System.getProperty("user.home"), ".netrc")
  if (!netrcPath.isRegularFile()) {
    println("DEBUG: .netrc not found at $netrcPath - skipping auth from .netrc")
    return null
  }

  val machine = "packages.jetbrains.team"
  val credentials = parseNetrc(netrcPath, machine)
  if (credentials == null) {
    println("DEBUG: credentials for $machine are missing in $netrcPath - skipping auth from .netrc")
    return null
  }

  println("DEBUG: got credentials for $machine from $netrcPath")
  return credentials
}

private val authHeaderValue by lazy {
  val credentials = getAuthFromSystemProperties() ?: getAuthFromMavenSettingsXml() ?: getAuthFromNetrc()
                    ?: error("Unable to get credentials from system properties, settings.xml, and .netrc")
  "Basic " + Base64.getEncoder().encodeToString("${credentials.first}:${credentials.second}".toByteArray())
}

internal class UrlCache(val cacheFile: Path) {
  private val httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()

  private val cache: MutableMap<String, CacheEntry> by lazy {
    if (Files.exists(cacheFile)) {
      Json.decodeFromString<List<CacheEntry>>(Files.readString(cacheFile)).associateByTo(HashMap()) { it.path }
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

  fun putUrl(jarPath: String, url: String, hash: String): CacheEntry {
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
      ?.followRedirectsIfNeeded()
    val statusCode = response?.statusCode()
    if (statusCode == 401) {
      throw IllegalStateException("Not authorized: $url")
    }
    return statusCode == 200
  }

  @Suppress("DuplicatedCode")
  fun calculateHash(url: String, repo: JarRepository): String {
    val digest = MessageDigest.getInstance("SHA-256")
    println("Downloading '$url'")
    val requestBuilder = HttpRequest.newBuilder()
      .uri(URI.create(url))
      .GET()
    addAuthIfNeeded(repo, requestBuilder)

    val response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream())
      .followRedirectsIfNeeded()
    if (response.statusCode() != 200) {
      val body = response.body().use { it.readAllBytes() }.decodeToString()
      error("Cannot download $url: ${response.statusCode()}\n$body")
    }

    response.body().use { inputStream ->
      val buffer = ByteArray(8192)
      var bytesRead: Int
      while (inputStream.read(buffer).also { bytesRead = it } != -1) {
        digest.update(buffer, 0, bytesRead)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  private inline fun <reified T> HttpResponse<T>.followRedirectsIfNeeded(maxRedirects: Int = 5): HttpResponse<T> {
    var response = this
    for (i in 0..maxRedirects) {
      if (!isHttpResponseRedirect(response.statusCode())) {
        return response
      }

      val location = response.headers().firstValue("Location").orNull() ?: error("Missing Location header")
      val requestBuilder = HttpRequest.newBuilder().uri(URI.create(location)).method(this.request().method(), HttpRequest.BodyPublishers.noBody())
      @Suppress("UNCHECKED_CAST")
      response = httpClient.send(requestBuilder.build(), when (T::class) {
        InputStream::class -> HttpResponse.BodyHandlers.ofInputStream()
        Void::class -> HttpResponse.BodyHandlers.discarding()
        else -> throw IllegalArgumentException("Unsupported type: ${T::class}")
      } as HttpResponse.BodyHandler<T>)
    }

    if (isHttpResponseRedirect(response.statusCode())) {
      error("Too many redirects: ${this.request().uri()}")
    }
    return response
  }
}

private fun addAuthIfNeeded(url: JarRepository, requestBuilder: HttpRequest.Builder) {
  if (url.isPrivate) {
    requestBuilder.header("Authorization", authHeaderValue)
  }
}

private fun isHttpResponseRedirect(statusCode: Int?): Boolean {
  return when (statusCode) {
    301,          // Moved Permanently
    302,          // Found
    303,          // See Other
    307,          // Temporary Redirect
    308  -> true  // Permanent Redirect
    else -> false
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