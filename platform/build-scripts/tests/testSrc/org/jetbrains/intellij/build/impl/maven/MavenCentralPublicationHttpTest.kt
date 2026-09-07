package org.jetbrains.intellij.build.impl.maven

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.io.HttpTestServer
import org.jetbrains.intellij.build.io.respond
import org.jetbrains.intellij.build.io.withHttpClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.net.http.HttpRequest
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

@Timeout(20)
class MavenCentralPublicationHttpTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `the bundle and status requests preserve the Sonatype format`() {
    val contents = ByteArray(256 * 1024) { (it % 251).toByte() }
    val bundle = Files.write(tempDir.resolve("bundle.zip"), contents)
    val requests = AtomicInteger()
    val authorization = "Bearer " + Base64.getEncoder().encodeToString("user:token".toByteArray(Charsets.UTF_8))
    HttpTestServer { exchange ->
      assertThat(exchange.requestMethod).isEqualTo("POST")
      assertThat(exchange.requestHeaders.getFirst("Authorization")).isEqualTo(authorization)
      when (requests.incrementAndGet()) {
        1 -> {
          assertThat(exchange.requestURI.toString()).isEqualTo("/upload?name=test&publishingType=USER_MANAGED")
          val contentType = exchange.requestHeaders.getFirst("Content-Type")
          assertThat(contentType).startsWith("multipart/form-data; boundary=")
          val boundary = contentType.substringAfter("boundary=")
          val prefix = "--$boundary\r\n" +
                       "Content-Disposition: form-data; name=\"bundle\"; filename=\"bundle.zip\"\r\n" +
                       "Content-Length: ${contents.size}\r\n\r\n"
          val expected = prefix.toByteArray() + contents + "\r\n--$boundary--\r\n".toByteArray()
          assertThat(exchange.requestHeaders.getFirst("Content-Length")).isEqualTo(expected.size.toString())
          assertThat(exchange.requestBody.readAllBytes()).isEqualTo(expected)
          exchange.respond("deployment-id", 201)
        }
        2 -> {
          assertThat(exchange.requestURI.toString()).isEqualTo("/status?id=deployment-id")
          assertThat(exchange.requestHeaders.getFirst("Content-Type")).isEqualTo("application/json; charset=utf-8")
          assertThat(exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)).isEqualTo("{}")
          exchange.respond("{\"deploymentState\":\"VALIDATED\"}")
        }
        else -> error("The publisher sent an extra request")
      }
    }.use { server ->
      withHttpClient(connectTimeout = 5.seconds) { client ->
        val upload = sendSonatypeRequest(
          client, server.uri.resolve("/upload?name=test&publishingType=USER_MANAGED"), "user", "token", 5.seconds,
        ) { it.mavenCentralBundle(bundle) }
        assertThat(upload.statusCode()).isEqualTo(201)
        assertThat(upload.body()).isEqualTo("deployment-id")
        val status = sendSonatypeRequest(client, server.uri.resolve("/status?id=${upload.body()}"), "user", "token", 5.seconds) {
          it.header("Content-Type", "application/json; charset=utf-8").POST(HttpRequest.BodyPublishers.ofString("{}"))
        }
        assertThat(status.body()).isEqualTo("{\"deploymentState\":\"VALIDATED\"}")
      }
    }
    assertThat(requests).hasValue(2)
    Files.delete(bundle)
  }

  @Test
  fun `a failed bundle upload sends once`() {
    val bundle = Files.writeString(tempDir.resolve("bundle.zip"), "bundle")
    val requests = AtomicInteger()
    HttpTestServer { exchange ->
      requests.incrementAndGet()
      exchange.requestBody.readAllBytes()
      exchange.respond("unavailable", 503)
    }.use { server ->
      withHttpClient(connectTimeout = 5.seconds) { client ->
        val response = sendSonatypeRequest(client, server.uri, "user", "token", 5.seconds) { it.mavenCentralBundle(bundle) }
        assertThat(response.statusCode()).isEqualTo(503)
        assertThat(response.body()).isEqualTo("unavailable")
      }
    }
    assertThat(requests).hasValue(1)
  }

  @Test
  fun `missing credentials fail before sending a request`() {
    val requests = AtomicInteger()
    HttpTestServer { exchange ->
      requests.incrementAndGet()
      exchange.respond("unexpected")
    }.use { server ->
      withHttpClient(connectTimeout = 5.seconds) { client ->
        for ((userName, token) in listOf(null to "token", "user" to null)) {
          assertThrows<IllegalArgumentException> {
            sendSonatypeRequest(client, server.uri, userName, token, 5.seconds) { it.GET() }
          }
        }
      }
    }
    assertThat(requests).hasValue(0)
  }
}
