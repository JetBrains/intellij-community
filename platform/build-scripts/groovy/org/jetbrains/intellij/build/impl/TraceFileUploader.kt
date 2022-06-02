// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import com.fasterxml.jackson.jr.ob.JSON
import org.jetbrains.intellij.build.toUrlWithTrailingSlash
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

open class TraceFileUploader(serverUrl: String, token: String?) {
  private val serverUrl = toUrlWithTrailingSlash(serverUrl)
  private val serverAuthToken = token

  protected open fun log(message: String) {}

  fun upload(file: Path, metadata: Map<String, String>) {
    log("Preparing to upload $file to $serverUrl")
    if (!Files.exists(file)) {
      throw RuntimeException("The file $file does not exist")
    }

    val id = uploadMetadata(getFullMetadata(file, metadata))
    log("Performed metadata upload. Import id is: $id")
    val response = uploadFile(file, id)
    log("Performed file upload. Server answered: $response")
  }

  private fun uploadMetadata(metadata: Map<String, String>): String {
    val postUrl = "${serverUrl}import"
    log("Posting to url $postUrl")
    val conn = URL(postUrl).openConnection() as HttpURLConnection
    conn.doInput = true
    conn.doOutput = true
    conn.useCaches = false
    conn.instanceFollowRedirects = true
    conn.requestMethod = "POST"
    val metadataContent = JSON.std.asString(metadata)
    log("Uploading metadata: $metadataContent")
    val content = metadataContent.toByteArray(StandardCharsets.UTF_8)
    conn.setRequestProperty("User-Agent", "TraceFileUploader")
    conn.setRequestProperty("Connection", "Keep-Alive")
    conn.setRequestProperty("Accept", "text/plain;charset=UTF-8")
    conn.setRequestProperty("Accept-Charset", StandardCharsets.UTF_8.name())
    conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8")
    if (serverAuthToken != null) {
      conn.setRequestProperty("Authorization", "Bearer $serverAuthToken")
    }
    conn.setRequestProperty("Content-Length", content.size.toString())
    conn.setFixedLengthStreamingMode(content.size)

    conn.outputStream.use { it.write(content) }

    // Get the response
    val code = conn.responseCode
    if (code == 200 || code == 201 || code == 202 || code == 204) {
      return readPlainMetadata(conn)
    }
    else {
      throw readError(conn, code)
    }
  }

  private fun uploadFile(file: Path, id: String): String {
    val postUrl = "${serverUrl}import/${URLEncoder.encode(id, StandardCharsets.UTF_8)}/upload/tr-single"
    log("Posting to url $postUrl")
    val connection = URL(postUrl).openConnection() as HttpURLConnection
    connection.doInput = true
    connection.doOutput = true
    connection.useCaches = false
    connection.requestMethod = "POST"
    connection.setRequestProperty("User-Agent", "TraceFileUploader")
    connection.setRequestProperty("Connection", "Keep-Alive")
    connection.setRequestProperty("Accept-Charset", StandardCharsets.UTF_8.name())
    connection.setRequestProperty("Content-Type", "application/octet-stream")
    if (serverAuthToken != null) {
      connection.setRequestProperty("Authorization", "Bearer $serverAuthToken")
    }
    val size = Files.size(file)
    connection.setRequestProperty("Content-Length", size.toString())
    connection.setFixedLengthStreamingMode(size)
    connection.outputStream.use {
      Files.copy(file, it)
    }

    // Get the response
    return readBody(connection)
  }
}

private fun getFullMetadata(file: Path, metadata: Map<String, String>): Map<String, String> {
  val map = LinkedHashMap(metadata)
  map.put("internal.upload.file.name", file.fileName.toString())
  map.put("internal.upload.file.path", file.toString())
  map.put("internal.upload.file.size", Files.size(file).toString())
  return map
}

private fun readBody(connection: HttpURLConnection): String {
  return connection.inputStream.use { it.readAllBytes().toString(Charsets.UTF_8) }
}

private fun readError(connection: HttpURLConnection, code: Int): Exception {
  val body = readBody(connection)
  return IOException("Unexpected code from server: $code body: $body")
}

private fun readPlainMetadata(connection: HttpURLConnection): String {
  val body = readBody(connection).trim()
  if (body.startsWith('{')) {
    val `object` = JSON.std.mapFrom(body)
    return (`object` as Map<*, *>).get("id") as String
  }

  try {
    return body.toLong().toString()
  }
  catch (ignored: NumberFormatException) {
  }
  throw IOException("Server returned neither import json nor id: $body")
}