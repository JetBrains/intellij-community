// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import com.fasterxml.jackson.jr.ob.JSON
import okhttp3.CacheControl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jetbrains.intellij.build.impl.compilation.executeAsync
import org.jetbrains.intellij.build.impl.compilation.httpClient
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

open class TraceFileUploader(serverUrl: String, token: String?) {
  private val serverUrl = serverUrl.trimEnd('/')
  private val serverAuthToken = token

  protected open fun log(message: String) {}

  suspend fun upload(file: Path, metadata: Map<String, String>) {
    log("Preparing to upload '$file' to '$serverUrl'")
    if (!Files.exists(file)) {
      throw RuntimeException("The file does not exist: $file")
    }

    val id = uploadMetadata(getFullMetadata(file, metadata))
    log("Performed metadata upload. Import id is: $id")
    val response = uploadFile(file, id)
    log("Performed file upload. Server answered: $response")
  }

  private suspend fun uploadMetadata(metadata: Map<String, String>): String {
    val url = "$serverUrl/import"
    val content = JSON.std.asString(metadata)
    log("Uploading metadata to '$url': $content")
    val builder = prepareRequestBuilder(url)
    builder.post(content.toRequestBody("application/json".toMediaType()))
    httpClient.newCall(builder.build()).executeAsync().use { response ->
      when (response.code) {
        200, 201, 202, 204 -> return readPlainMetadata(response)
        else -> throw readError(response, response.code)
      }
    }
  }

  private suspend fun uploadFile(file: Path, id: String): String {
    val url = "$serverUrl/import/${URLEncoder.encode(id, StandardCharsets.UTF_8)}/upload/tr-single"
    log("Uploading '${file.fileName}' to '$url'")
    val builder = prepareRequestBuilder(url)
    builder.post(file.toFile().asRequestBody("application/octet-stream".toMediaType()))
    httpClient.newCall(builder.build()).executeAsync().use { response ->
      return readBody(response)
    }
  }

  private fun prepareRequestBuilder(url: String): Request.Builder {
    val builder = Request.Builder().url(url)
    builder.cacheControl(CacheControl.FORCE_NETWORK)
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

private fun readBody(connection: Response): String {
  return connection.body.use { body ->
    body.string()
  }
}

private fun readError(connection: Response, code: Int): Exception {
  val body = readBody(connection)
  return IOException("Unexpected code from server: $code body: $body")
}

private fun readPlainMetadata(connection: Response): String {
  val body = readBody(connection).trim()
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