// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.io.sendHttpRequest
import org.jetbrains.intellij.build.io.withHttpClient
import tools.jackson.jr.ob.JSON
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

open class TraceFileUploader(serverUrl: String, token: String?) {
  private val serverUrl = serverUrl.trimEnd('/')
  private val serverAuthToken = token

  protected open fun log(message: String) {}

  /** Uploads [file] with its [metadata], and blocks the calling thread while the two requests run. */
  fun upload(file: Path, metadata: Map<String, String>) {
    log("Preparing to upload '$file' to '$serverUrl'")
    if (!Files.exists(file)) {
      throw RuntimeException("The file does not exist: $file")
    }

    withHttpClient(connectTimeout = 1.minutes) { client ->
      val id = uploadMetadata(client, getFullMetadata(file, metadata))
      log("Performed metadata upload. Import id is: $id")
      val response = uploadFile(client, file, id)
      log("Performed file upload. Server answered: $response")
    }
  }

  private fun uploadMetadata(client: HttpClient, metadata: Map<String, String>): String {
    val url = "$serverUrl/import"
    val content = JSON.std.asString(metadata)
    log("Uploading metadata to '$url': $content")
    val builder = prepareRequestBuilder(url)
    builder.header("Content-Type", "application/json; charset=utf-8")
    builder.POST(HttpRequest.BodyPublishers.ofString(content))
    val response = sendHttpRequest(client, builder.build(), timeout = 1.minutes)
    when (val code = response.statusCode()) {
      200, 201, 202, 204 -> return readPlainMetadata(response.body())
      else -> throw IOException("Unexpected code from server: $code body: ${response.body()}")
    }
  }

  private fun uploadFile(client: HttpClient, file: Path, id: String): String {
    val url = "$serverUrl/import/${URLEncoder.encode(id, StandardCharsets.UTF_8)}/upload/tr-single"
    log("Uploading '${file.fileName}' to '$url'")
    val builder = prepareRequestBuilder(url)
    builder.header("Content-Type", "application/octet-stream")
    builder.POST(HttpRequest.BodyPublishers.ofFile(file))
    return sendHttpRequest(client, builder.build(), timeout = 1.minutes).body()
  }

  private fun prepareRequestBuilder(url: String): HttpRequest.Builder {
    val builder = HttpRequest.newBuilder(URI(url))
    builder.header("Cache-Control", "no-cache")
    builder.header("User-Agent", "TraceFileUploader")
    if (serverAuthToken != null) {
      builder.header("Authorization", "Bearer $serverAuthToken")
    }
    builder.header("Accept", "text/plain;charset=UTF-8")
    builder.header("Accept-Charset", StandardCharsets.UTF_8.name())
    return builder
  }
}

private fun getFullMetadata(file: Path, metadata: Map<String, String>): Map<String, String> {
  val map = LinkedHashMap(metadata)
  map.put("internal.upload.file.name", file.fileName.toString())
  map.put("internal.upload.file.path", file.toString())
  map.put("internal.upload.file.size", Files.size(file).toString())
  return map
}

private fun readPlainMetadata(content: String): String {
  val body = content.trim()
  if (body.startsWith('{')) {
    val map = JSON.std.mapFrom(body)
    return map.get("id") as String
  }

  try {
    return body.toLong().toString()
  }
  catch (ignored: NumberFormatException) {
  }
  throw IOException("Server returned neither import json nor id: $body")
}
