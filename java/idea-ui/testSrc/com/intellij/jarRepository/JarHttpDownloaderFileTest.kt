// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository

import com.intellij.jarRepository.JarHttpDownloaderTestUtil.TestHttpServerExtension
import com.intellij.jarRepository.JarHttpDownloaderTestUtil.createContext
import com.intellij.jarRepository.JarHttpDownloaderTestUtil.url
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.sha256Hex
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.idea.maven.aether.Retry
import org.jetbrains.idea.maven.aether.RetryProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JarHttpDownloaderFileTest {
  @JvmField
  @RegisterExtension
  internal val serverExtension = TestHttpServerExtension { server ->
    server.createContext("/demo.data", HttpStatusCode.OK, response = "Hello, world!")
  }
  private val server: ApplicationEngine get() = serverExtension.server

  @JvmField
  @RegisterExtension
  val tempDirectory = TempDirectoryExtension()

  @BeforeEach
  fun setUp() {
    JarHttpDownloader.forceHttps = false
  }

  @AfterEach
  fun tearDown() {
    JarHttpDownloader.forceHttps = true
  }

  @Test
  fun downloadFile_force_https() {
    JarHttpDownloader.forceHttps = true
    val file = tempDirectory.rootPath.resolve("ant.pom")
    val url = server.url + "/demo.data"
    val exception = assertFailsWith<IllegalStateException> {
      JarHttpDownloader.downloadFile(url, file, RetryProvider.disabled())
    }
    assertEquals("Url must have https protocol: $url", exception.message)
  }

  @Test
  fun downloadFile_retries() {
    val log = StringBuilder()

    val retry = RetryProvider.withExponentialBackOff(1, 1, 3)

    for (code in listOf(HttpStatusCode.Unauthorized, HttpStatusCode.NotFound, HttpStatusCode.InternalServerError)) {
      val url = server.url + "/code-${code.value}"
      server.createContext("/${url.substringAfterLast('/')}", code, response = "Hello, world!", log = {
        log.appendLine("reply with $code")
      })

      val file = tempDirectory.rootPath.resolve("x")
      val exception = assertFailsWith<HttpRequests.HttpStatusException> {
        JarHttpDownloader.downloadFile(url, file, retry = retry)
      }
      assertEquals(code.value, exception.statusCode)
    }

    assertEquals("""
        /code-401: 401
        /code-404: 404
        /code-500: 500
        /code-500: 500
        /code-500: 500
    """.trimIndent(), serverExtension.log.trim())
  }

  @Test
  fun downloadFile_retry_recovery() {
    val log = StringBuilder()

    val retry = RetryProvider.withExponentialBackOff(1, 1, 3)

    val url = server.url + "/x"
    val countOfInternalServerErrors = AtomicInteger(2)
    server.application.routing {
      get("/${url.substringAfterLast('/')}") {
        val code = if (countOfInternalServerErrors.decrementAndGet() >= 0) HttpStatusCode.InternalServerError else HttpStatusCode.OK
        call.respond(code, "Hello, world!")
        log.appendLine("reply with ${code.value}")
      }
    }

    val file = tempDirectory.rootPath.resolve("x")
    JarHttpDownloader.downloadFile(url, file, retry = retry)
    assertEquals("Hello, world!", file.readText())

    assertEquals("""
      reply with 500
      reply with 500
      reply with 200
    """.trimIndent(), log.toString().trim())
  }

  @Test
  fun downloadFile_retry_connection_refused() {
    val bindAddr = InetAddress.getByName("127.0.0.1")
    val serverPort = ServerSocket(0, 10, bindAddr).use { it.localPort }

    val attempts = AtomicInteger(0)

    val originalRetry = RetryProvider.withExponentialBackOff(1, 1, 3)
    val retry = object : Retry {
      override fun <R : Any?> retry(computable: ThrowableComputable<out R?, out Exception>, logger: Logger): R? {
        return originalRetry.retry(ThrowableComputable {
          attempts.incrementAndGet()
          computable.compute()
         }, logger)
      }
    }

    val url = "http://127.0.0.1:${serverPort}/x"
    val exception = assertFailsWith<IOException> {
      JarHttpDownloader.downloadFile(url, tempDirectory.rootPath.resolve("x"), retry = retry)
    }

    assertTrue(exception.message!!.contains("Connection refused"), exception.message)
    assertEquals(3, attempts.get())
  }

  @Test
  fun downloadFile_fail_on_missing_content_length() {
    server.application.routing {
      get("/unknown-content-length.data") {
        call.respond(object : OutgoingContent.ByteArrayContent() {
          override fun bytes(): ByteArray = "Hello, world!".toByteArray()
          override val contentLength: Long? = null
        })
      }
    }

    val file = tempDirectory.rootPath.resolve("x")
    val url = server.url + "/unknown-content-length.data"
    val exception = assertFailsWith<IllegalStateException> {
      JarHttpDownloader.downloadFile(url, file, RetryProvider.disabled())
    }
    assertEquals("Header 'Content-Length' is missing or zero for $url", exception.message)
  }

  @Test
  fun downloadFile_no_expected_checksum() {
    val file = tempDirectory.rootPath.resolve("ant.pom")
    JarHttpDownloader.downloadFile(server.url + "/demo.data", file, RetryProvider.disabled())
    assertEquals(13, file.fileSize())
    assertEquals("315f5bdb76d078c43b8ac0064e4a0164612b1fce77c869345bfc94c75894edd3", sha256Hex(file))
  }

  @Test
  fun downloadFile_expected_checksum() {
    val file = tempDirectory.rootPath.resolve("ant.pom")
    JarHttpDownloader.downloadFile(
      server.url + "/demo.data", file, RetryProvider.disabled(),
      expectedSha256 = "315f5bdb76d078c43b8ac0064e4a0164612b1fce77c869345bfc94c75894edd3")
    assertEquals(13, file.fileSize())
    assertEquals("315f5bdb76d078c43b8ac0064e4a0164612b1fce77c869345bfc94c75894edd3", sha256Hex(file))
  }

  @Test
  fun downloadFile_wrong_checksum() {
    val attempts = AtomicInteger(0)

    val originalRetry = RetryProvider.withExponentialBackOff(1, 1, 3)
    val retry = object : Retry {
      override fun <R : Any?> retry(computable: ThrowableComputable<out R?, out Exception>, logger: Logger): R? {
        return originalRetry.retry(ThrowableComputable {
          attempts.incrementAndGet()
          computable.compute()
        }, logger)
      }
    }

    val file = tempDirectory.rootPath.resolve("ant.pom")
    val url = server.url + "/demo.data"
    val exception = assertFailsWith<JarHttpDownloader.BadChecksumException> {
      JarHttpDownloader.downloadFile(
        url, file, retry,
        expectedSha256 = "b6276017cf6f2a07b7b7b62778333237ca73405fcc3af1ca9d95f52f97fb7000")
    }
    assertFalse(file.exists())
    assertTrue(exception.message!!.startsWith("Wrong file checksum after downloading"), exception.message)
    assertEquals(1, attempts.get(), "Only one attempt should be made")
  }
}