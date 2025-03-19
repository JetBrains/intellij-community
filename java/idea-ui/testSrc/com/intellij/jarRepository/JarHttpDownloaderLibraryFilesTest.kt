// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository

import com.intellij.jarRepository.JarHttpDownloader.RemoteRepository
import com.intellij.jarRepository.JarHttpDownloaderTestUtil.TestHttpServerExtension
import com.intellij.jarRepository.JarHttpDownloaderTestUtil.createContext
import com.intellij.jarRepository.JarHttpDownloaderTestUtil.url
import com.intellij.testFramework.rules.TempDirectoryExtension
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.ApplicationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.aether.RetryProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.fail
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JarHttpDownloaderLibraryFilesTest {
  @JvmField
  @RegisterExtension
  internal val serverExtension = TestHttpServerExtension()
  private val server: ApplicationEngine get() = serverExtension.server.engine

  @JvmField
  @RegisterExtension
  internal val tempDirectory = TempDirectoryExtension()

  private val localRepository by lazy {
    tempDirectory.newDirectoryPath("local")
  }

  private val remoteRepositories by lazy {
    listOf(
      RemoteRepository(server.url + "/a", null),
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

  // test that we wait for all downloads even if one of the downloads fails
  // also tests that we handle connections in parallel
  @Test
  fun downloadLibraryFilesAsync_finish_all_files() {
    val response = "data"

    serverExtension.server.application.createContext("/a/fail.data", HttpStatusCode.NotFound, response = response)
    serverExtension.server.application.createContext("/a/delay.data", HttpStatusCode.OK, response = response, delayMs = 500)

    runBlocking(Dispatchers.IO) {
      val failure = assertFailsWith<IllegalStateException> {
        JarHttpDownloader.downloadLibraryFilesAsync(
          relativePaths = listOf(
            JarHttpDownloader.RelativePathToDownload(Path.of("fail.data"), null),
            JarHttpDownloader.RelativePathToDownload(Path.of("delay.data"), null),
          ),
          localRepository = localRepository,
          remoteRepositories = remoteRepositories,
          retry = RetryProvider.disabled(),
          downloadDispatcher = Dispatchers.IO,
        )
      }

      val message = "Failed to download 1 artifact(s): (first exception) " +
                    "Artifact 'fail.data' was not found in remote repositories: [${server.url}/a/fail.data]"
      if (failure.message != message) {
        fail(
          "Expected message: '$message'\n" +
          "Actual message: '${failure.message}'\n" +
          "Stacktrace:\n" +
          failure.stackTraceToString())
      }

      assertEquals("Artifact 'fail.data' was not found in remote repositories: [${server.url}/a/fail.data]", failure.cause!!.message)

      assertEquals(response, localRepository.resolve("delay.data").readText())
    }

    assertEquals("""
      /a/fail.data: 404
      /a/delay.data: 200
    """.trimIndent(), serverExtension.log.trim())
  }

  @Test
  fun downloadLibraryFilesAsync_smoke() {
    serverExtension.server.application.createContext("/a/ok.data", HttpStatusCode.OK, response = "data")

    runBlocking {
      val files = JarHttpDownloader.downloadLibraryFilesAsync(
        relativePaths = listOf(
          JarHttpDownloader.RelativePathToDownload(Path.of("ok.data"), null),
        ),
        localRepository = localRepository,
        remoteRepositories = remoteRepositories,
        retry = RetryProvider.disabled(),
        downloadDispatcher = Dispatchers.IO,
      )
      assertEquals(localRepository.resolve("ok.data"), files.single())
      assertEquals("data", localRepository.resolve("ok.data").readText())
    }
  }
}