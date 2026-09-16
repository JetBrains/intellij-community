package org.jetbrains.intellij.build.impl

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.io.HttpTestServer
import org.jetbrains.intellij.build.io.respond
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

@Timeout(20)
class TraceFileUploaderTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `metadata and file uploads preserve the request format`() {
    val contents = ByteArray(256 * 1024) { (it % 251).toByte() }
    val file = Files.write(tempDir.resolve("trace.bin"), contents)
    for ((metadataResponse, encodedId) in listOf(" 00123 " to "123", "{\"id\":\"id /+\"}" to "id+%2F%2B")) {
      val requests = AtomicInteger()
      HttpTestServer { exchange ->
        assertThat(exchange.requestMethod).isEqualTo("POST")
        assertThat(exchange.requestHeaders.getFirst("Authorization")).isEqualTo("Bearer test-token")
        assertThat(exchange.requestHeaders.getFirst("User-Agent")).isEqualTo("TraceFileUploader")
        assertThat(exchange.requestHeaders.getFirst("Cache-Control")).isEqualTo("no-cache")
        when (requests.incrementAndGet()) {
          1 -> {
            assertThat(exchange.requestURI.path).isEqualTo("/import")
            assertThat(exchange.requestHeaders.getFirst("Content-Type")).isEqualTo("application/json; charset=utf-8")
            assertThat(exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)).contains(
              "\"project\":\"test\"",
              "\"internal.upload.file.name\":\"trace.bin\"",
              "\"internal.upload.file.size\":\"${contents.size}\"",
            )
            exchange.respond(metadataResponse, 201)
          }
          2 -> {
            assertThat(exchange.requestURI.rawPath).isEqualTo("/import/$encodedId/upload/tr-single")
            assertThat(exchange.requestHeaders.getFirst("Content-Type")).isEqualTo("application/octet-stream")
            assertThat(exchange.requestBody.readAllBytes()).isEqualTo(contents)
            exchange.respond("uploaded")
          }
          else -> error("The uploader sent an extra request")
        }
      }.use { server ->
        TraceFileUploader(server.uri.toString(), "test-token").upload(file, mapOf("project" to "test"))
      }
      assertThat(requests).hasValue(2)
    }
    Files.delete(file)
  }

  @Test
  fun `a metadata failure does not retry or upload the file`() {
    val file = Files.writeString(tempDir.resolve("trace.bin"), "trace")
    val requests = AtomicInteger()
    HttpTestServer { exchange ->
      requests.incrementAndGet()
      exchange.requestBody.readAllBytes()
      exchange.respond("unavailable", 503)
    }.use { server ->
      val failure = assertThrows<IOException> {
        TraceFileUploader(server.uri.toString(), null).upload(file, emptyMap())
      }
      assertThat(failure).hasMessage("Unexpected code from server: 503 body: unavailable")
    }
    assertThat(requests).hasValue(1)
  }

  @Test
  fun `an invalid metadata response stops the upload`() {
    val file = Files.writeString(tempDir.resolve("trace.bin"), "trace")
    val requests = AtomicInteger()
    HttpTestServer { exchange ->
      requests.incrementAndGet()
      exchange.requestBody.readAllBytes()
      exchange.respond("invalid")
    }.use { server ->
      assertThat(assertThrows<IOException> {
        TraceFileUploader(server.uri.toString(), null).upload(file, emptyMap())
      }).hasMessageContaining("Server returned neither import json nor id")
    }
    assertThat(requests).hasValue(1)
  }
}
