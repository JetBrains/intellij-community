// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.downloadFileToCacheLocation
import org.junit.Assert
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

class BuildDependenciesDownloaderTest {
  @Test
  fun getUriForMavenArtifact() {
    val uri = BuildDependenciesDownloader.getUriForMavenArtifact(
      "https://my-host/path",
      "org.groupId",
      "artifactId",
      "1.1",
      "zip"
    )
    Assert.assertEquals("https://my-host/path/org/groupId/artifactId/1.1/artifactId-1.1.zip", uri.toString())
  }

  @Test
  fun getUriForMavenArtifact_classifier() {
    val uri = BuildDependenciesDownloader.getUriForMavenArtifact(
      "https://my-host/path",
      "org.groupId",
      "artifactId",
      "1.1",
      "bin",
      "zip"
    )
    Assert.assertEquals("https://my-host/path/org/groupId/artifactId/1.1/artifactId-1.1-bin.zip", uri.toString())
  }

  @Test
  fun `downloadFileToCacheLocation - cached on second call from default dispatcher`() = runBlocking(Dispatchers.Default) {
    val requestCount = AtomicInteger()
    val content = "downloaded-${System.nanoTime()}"
    val path = "/test-${System.nanoTime()}.txt"
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    var serverStopped = false
    server.createContext(path) { exchange ->
      requestCount.incrementAndGet()
      val response = content.toByteArray()
      exchange.sendResponseHeaders(200, response.size.toLong())
      exchange.responseBody.use { output ->
        output.write(response)
      }
    }
    server.start()
    try {
      val url = "http://127.0.0.1:${server.address.port}$path"
      val firstDownload = downloadFileToCacheLocation(url, BuildPaths.COMMUNITY_ROOT)
      Assert.assertEquals(content, Files.readString(firstDownload))

      server.stop(0)
      serverStopped = true

      val cachedDownload = downloadFileToCacheLocation(url, BuildPaths.COMMUNITY_ROOT)
      Assert.assertEquals(firstDownload, cachedDownload)
      Assert.assertEquals(content, Files.readString(cachedDownload))
      Assert.assertEquals(1, requestCount.get())
    }
    finally {
      if (!serverStopped) {
        server.stop(0)
      }
    }
  }
}