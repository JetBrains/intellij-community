// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository

import com.intellij.jarRepository.JarHttpDownloader.RemoteRepository
import com.intellij.jarRepository.JarHttpDownloader.downloadArtifact
import com.intellij.jarRepository.JarHttpDownloaderTestUtil.createContext
import com.intellij.jarRepository.JarHttpDownloaderTestUtil.url
import com.intellij.jarRepository.JarRepositoryAuthenticationDataProvider.AuthenticationData
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.util.io.DigestUtil
import com.intellij.util.io.HttpRequests
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.ApplicationEngine
import org.jetbrains.idea.maven.aether.RetryProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JarHttpDownloaderArtifactTest {
  @JvmField
  @RegisterExtension
  internal val serverExtension = JarHttpDownloaderTestUtil.TestHttpServerExtension()
  private val server: ApplicationEngine get() = serverExtension.server

  @JvmField
  @RegisterExtension
  internal val tempDirectory = TempDirectoryExtension()

  private val localRepository by lazy {
    tempDirectory.newDirectoryPath("local")
  }

  private val remoteRepositories by lazy {
    listOf(
      RemoteRepository(server.url + "/a", null),
      RemoteRepository(server.url + "/b", AuthenticationData("u", "pass")),
      RemoteRepository(server.url + "/c", null),
    )
  }

  @BeforeEach
  fun setUp() {
    JarHttpDownloader.forceHttps = false

  }

  @AfterEach
  fun tearDown() {
    JarHttpDownloader.forceHttps = true
  }

  @Test
  fun downloadArtifact_second_succeed() {
    server.createContext("/b/c/file.data", HttpStatusCode.OK, response = "Hello, world!")

    val localFile = downloadArtifact(
      artifactPath = JarHttpDownloader.RelativePathToDownload(Path.of("c/file.data"), null),
      localRepository = localRepository,
      remoteRepositories = remoteRepositories,
      retry = RetryProvider.disabled(),
    )

    assertEquals(localRepository.resolve("c/file.data"), localFile)
    assertEquals("Hello, world!", localFile.readText())

    assertEquals("""
      /a/c/file.data: 404
      /b/c/file.data: 200
    """.trimIndent(), serverExtension.log.trim())
  }

  @Test
  fun downloadArtifact_authenticate() {
    server.createContext("/b/c/file.data", HttpStatusCode.OK, response = "Hello, world!", auth = AuthenticationData("u", "pass"))

    val localFile = downloadArtifact(
      artifactPath = JarHttpDownloader.RelativePathToDownload(Path.of("c/file.data"), null),
      localRepository = localRepository,
      remoteRepositories = remoteRepositories,
      retry = RetryProvider.disabled(),
    )

    assertEquals(localRepository.resolve("c/file.data"), localFile)
    assertEquals("Hello, world!", localFile.readText())
  }

  @Test
  fun downloadArtifact_authenticate_wrong_password() {
    server.createContext("/b/c/file.data", HttpStatusCode.OK, response = "Hello, world!", auth = AuthenticationData("u", "another password"))

    val e = assertFailsWith<IllegalStateException> {
      downloadArtifact(
        artifactPath = JarHttpDownloader.RelativePathToDownload(Path.of("c/file.data"), null),
        localRepository = localRepository,
        remoteRepositories = remoteRepositories,
        retry = RetryProvider.disabled(),
      )
    }

    assertEquals("Artifact 'c/file.data' was not found in remote repositories, some of them returned 401 Unauthorized: [${server.url}/b/c/file.data]", e.message)
    val suppressed = e.suppressedExceptions.single() as HttpRequests.HttpStatusException
    assertEquals("Request failed with status code 401", suppressed.message)
    assertEquals("${server.url}/b/c/file.data", suppressed.url)
  }

  @Test
  fun downloadArtifact_skip_wrong_authentication() {
    // repository 'a': artifact missing
    // repository 'b': wrong authentication
    // repository 'c': OK
    server.createContext("/b/c/file.data", HttpStatusCode.OK, response = "secret data", auth = AuthenticationData("u", "another password"))
    server.createContext("/c/c/file.data", HttpStatusCode.OK, response = "Hello, world!")

    downloadArtifact(
      artifactPath = JarHttpDownloader.RelativePathToDownload(Path.of("c/file.data"), null),
      localRepository = localRepository,
      remoteRepositories = remoteRepositories,
      retry = RetryProvider.disabled(),
    )

    assertEquals("Hello, world!", localRepository.resolve("c/file.data").readText())

    assertEquals("""
      /a/c/file.data: 404
      /b/c/file.data: 401
      /c/c/file.data: 200
    """.trimIndent(), serverExtension.log.trim())
  }

  @Test
  fun downloadArtifact_do_not_skip_internal_server_error() {
    // repository 'a': artifact missing
    // repository 'b': 500
    // ... we should not query 'c' for security reasons (when checksum missing, we can't guarantee what we'll find in 'c')
    //
    // case when 'expectedSha256!=null' was not implemented (we may query everything then), but could be

    server.createContext("/b/c/file.data", HttpStatusCode.InternalServerError)

    val e = assertFailsWith<HttpRequests.HttpStatusException> {
      downloadArtifact(
        artifactPath = JarHttpDownloader.RelativePathToDownload(Path.of("c/file.data"), null),
        localRepository = localRepository,
        remoteRepositories = remoteRepositories,
        retry = RetryProvider.disabled(),
      )
    }

    assertEquals("Request failed with status code 500", e.message)
    assertEquals("${server.url}/b/c/file.data", e.url)

    assertEquals("""
      /a/c/file.data: 404
      /b/c/file.data: 500
    """.trimIndent(), serverExtension.log.trim())
  }

  @Test
  fun downloadArtifact_do_not_skip_connection_refused() {
    // repository 'a': artifact missing
    // repository 'b': connection refused
    // ... we should not query 'c' for security reasons (when checksum missing, we can't guarantee what we'll find in 'c')
    //
    // case when 'expectedSha256!=null' was not implemented (we may query everything then), but could be

    val bindAddr = InetAddress.getByName("127.0.0.1")
    val unusedLocalPort = ServerSocket(0, 10, bindAddr).use { it.localPort }

    val e = assertFailsWith<IOException> {
      downloadArtifact(
        artifactPath = JarHttpDownloader.RelativePathToDownload(Path.of("c/file.data"), null),
        localRepository = localRepository,
        remoteRepositories = listOf(
          RemoteRepository(server.url + "/a", null),
          RemoteRepository("http://127.0.0.1:${unusedLocalPort}/root", null),
          RemoteRepository(server.url + "/c", null),
        ),
        retry = RetryProvider.disabled(),
      )
    }

    assertTrue(e.message!!.contains("http://127.0.0.1:${unusedLocalPort}/root/c/file.data"), e.message)
    assertTrue(e.message!!.contains("Connection refused"), e.message)

    assertEquals("""
      /a/c/file.data: 404
    """.trimIndent(), serverExtension.log.trim())
  }

  @Test
  fun downloadArtifact_wrong_checksum() {
    val response = "Hello, world!"
    server.createContext("/b/c/file.data", HttpStatusCode.OK, response = response)

    val actualSha256 = DigestUtil.sha256Hex(response.toByteArray())
    val expectedSha256 = DigestUtil.sha256Hex("wrong".toByteArray())

    val e = assertFailsWith<JarHttpDownloader.BadChecksumException> {
      downloadArtifact(
        artifactPath = JarHttpDownloader.RelativePathToDownload(Path.of("c/file.data"), expectedSha256),
        localRepository = localRepository,
        remoteRepositories = remoteRepositories,
        retry = RetryProvider.disabled(),
      )
    }

    assertTrue(e.message!!.startsWith("Wrong file checksum after downloading '${server.url}/b/c/file.data'"), e.message)
    assertTrue(e.message!!.contains(expectedSha256), e.message)
    assertTrue(e.message!!.contains(actualSha256), e.message)
    assertTrue(e.message!!.contains("(fileSize: 13)"), e.message)

    assertEquals("""
      /a/c/file.data: 404
      /b/c/file.data: 200
    """.trimIndent(), serverExtension.log.trim())
  }

  @Test
  fun downloadArtifact_missing_artifact() {
    val e = assertFailsWith<IllegalStateException> {
      downloadArtifact(
        artifactPath = JarHttpDownloader.RelativePathToDownload(Path.of("c/file.data"), null),
        localRepository = localRepository,
        remoteRepositories = remoteRepositories,
        retry = RetryProvider.disabled(),
      )
    }

    assertEquals("Artifact 'c/file.data' was not found in remote repositories: [${server.url}/a/c/file.data, ${server.url}/b/c/file.data, ${server.url}/c/c/file.data]", e.message)

    assertEquals("""
      /a/c/file.data: 404
      /b/c/file.data: 404
      /c/c/file.data: 404
    """.trimIndent(), serverExtension.log.trim())
  }
}