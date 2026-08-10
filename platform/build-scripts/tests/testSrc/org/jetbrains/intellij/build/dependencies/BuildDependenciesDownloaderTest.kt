// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.downloadFileToCacheLocation
import org.jetbrains.intellij.build.resolveAndExtractToCacheLocation
import org.junit.Assert
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE
import java.nio.file.attribute.PosixFilePermission.OWNER_READ
import java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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

  @Test
  fun `preloaded manifest supplies cache and SHA changes its identity`() = runBlocking(Dispatchers.Default) {
    withPreloadedTestRoot { communityRoot, cache, manifestRoot ->
      val url = "https://example.invalid/artifact.bin"
      val source = manifestRoot.resolve("artifact.bin")
      Files.writeString(source, "first")
      writeManifest(manifestRoot, "artifact.bin", "1".repeat(64), url)

      val first = downloadFileToCacheLocation(url, communityRoot)
      Assert.assertTrue(first.startsWith(cache))
      Assert.assertEquals("first", Files.readString(first))
      Assert.assertEquals(first, downloadFileToCacheLocation(url, communityRoot))

      Files.writeString(source, "second")
      writeManifest(manifestRoot, "artifact.bin", "2".repeat(64), url)
      val repinned = downloadFileToCacheLocation(url, communityRoot)
      Assert.assertNotEquals(first, repinned)
      Assert.assertEquals("second", Files.readString(repinned))
    }
  }

  @Test
  fun `preloaded manifest rejects undeclared URL before network or cache lookup`() = runBlocking(Dispatchers.Default) {
    withPreloadedTestRoot { communityRoot, _, manifestRoot ->
      Files.writeString(manifestRoot.resolve("declared.bin"), "declared")
      writeManifest(manifestRoot, "declared.bin", "3".repeat(64), "https://example.invalid/declared.bin")

      val error = Assert.assertThrows(IllegalStateException::class.java) {
        runBlocking {
          downloadFileToCacheLocation("http://127.0.0.1:9/not-declared.bin", communityRoot)
        }
      }
      Assert.assertTrue(error.message, error.message!!.contains("not declared in authoritative"))
    }
  }

  @Test
  fun `preloaded manifest rejects malformed metadata and missing runfiles`() = runBlocking(Dispatchers.Default) {
    withPreloadedTestRoot { communityRoot, _, manifestRoot ->
      val manifest = manifestRoot.resolve("preloaded-downloads-v1.tsv")
      Files.writeString(manifest, "wrong-header\n")
      val malformed = Assert.assertThrows(IllegalStateException::class.java) {
        runBlocking { downloadFileToCacheLocation("https://example.invalid/missing.bin", communityRoot) }
      }
      Assert.assertTrue(malformed.message, malformed.message!!.contains("must start with"))

      writeManifest(manifestRoot, "missing.bin", "4".repeat(64), "https://example.invalid/missing.bin")
      val missing = Assert.assertThrows(IllegalStateException::class.java) {
        runBlocking { downloadFileToCacheLocation("https://example.invalid/missing.bin", communityRoot) }
      }
      Assert.assertTrue(missing.message, missing.message!!.contains("missing runfile"))
    }
  }

  @Test
  fun `extraction reads a preloaded archive without writing anywhere near it`() = runBlocking(Dispatchers.Default) {
    withPreloadedTestRoot { communityRoot, cache, manifestRoot ->
      val url = "https://example.invalid/preloaded.zip"
      val archive = manifestRoot.resolve("preloaded.zip")
      writeZip(archive, "hello.txt", "hello")
      writeManifest(manifestRoot, "preloaded.zip", "5".repeat(64), url)

      // the runfiles tree can be a read-only share; extraction must not need to write into it
      val readOnly = setOf(OWNER_READ, OWNER_EXECUTE)
      Files.setPosixFilePermissions(manifestRoot, readOnly)
      try {
        val extracted = resolveAndExtractToCacheLocation(url, communityRoot)
        Assert.assertTrue("$extracted must be under $cache", extracted.startsWith(cache))
        Assert.assertEquals("hello", Files.readString(extracted.resolve("hello.txt")))
        Assert.assertEquals(extracted, resolveAndExtractToCacheLocation(url, communityRoot))
      }
      finally {
        Files.setPosixFilePermissions(manifestRoot, setOf(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE))
      }
    }
  }

  @Test
  fun `extraction of the same content from two paths lands in one cache directory`() = runBlocking(Dispatchers.Default) {
    withPreloadedTestRoot { communityRoot, _, manifestRoot ->
      val url = "https://example.invalid/moving.zip"
      val sha256 = "6".repeat(64)
      val first = manifestRoot.resolve("a/moving.zip")
      Files.createDirectories(first.parent)
      writeZip(first, "payload.txt", "payload")
      Files.writeString(manifestRoot.resolve("preloaded-downloads-v1.tsv"), "intellij-build-downloads\t1\na/moving.zip\t$sha256\t$url\n")
      val fromFirstPath = resolveAndExtractToCacheLocation(url, communityRoot)

      // the same declared content, reached through the path a different test target or sandbox would give it
      val second = manifestRoot.resolve("b/moving.zip")
      Files.createDirectories(second.parent)
      writeZip(second, "payload.txt", "payload")
      Files.writeString(manifestRoot.resolve("preloaded-downloads-v1.tsv"), "intellij-build-downloads\t1\nb/moving.zip\t$sha256\t$url\n")
      val fromSecondPath = resolveAndExtractToCacheLocation(url, communityRoot)

      Assert.assertEquals(fromFirstPath, fromSecondPath)
      Assert.assertEquals("payload", Files.readString(fromSecondPath.resolve("payload.txt")))
    }
  }

  @Test
  fun `extraction keyed by path treats two presentations of one archive as one entry`() = runBlocking(Dispatchers.Default) {
    withPreloadedTestRoot { communityRoot, _, manifestRoot ->
      val archive = manifestRoot.resolve("local.zip")
      writeZip(archive, "payload.txt", "payload")
      writeManifest(manifestRoot, "local.zip", "7".repeat(64), "https://example.invalid/local.zip")

      val direct = extractFileToCacheLocation(archive, communityRoot)
      val dotted = extractFileToCacheLocation(archive.parent.resolve(".").resolve(archive.fileName), communityRoot)
      val roundTripped = extractFileToCacheLocation(
        archive.parent.resolve("..").resolve(manifestRoot.fileName).resolve(archive.fileName),
        communityRoot,
      )

      Assert.assertEquals(direct, dotted)
      Assert.assertEquals(direct, roundTripped)
      Assert.assertEquals("payload", Files.readString(direct.resolve("payload.txt")))
    }
  }

  private fun writeZip(target: Path, entryName: String, content: String) {
    ZipOutputStream(Files.newOutputStream(target)).use { zip ->
      zip.putNextEntry(ZipEntry(entryName))
      zip.write(content.toByteArray())
      zip.closeEntry()
    }
  }

  private suspend fun withPreloadedTestRoot(
    action: suspend (BuildDependenciesCommunityRoot, Path, Path) -> Unit,
  ) {
    val root = Files.createTempDirectory("preloaded-downloads-test")
    val community = root.resolve("community")
    val cache = root.resolve("cache")
    val manifestRoot = root.resolve("runfiles")
    Files.createDirectories(community)
    Files.createDirectories(manifestRoot)
    Files.createFile(community.resolve("intellij.idea.community.main.iml"))
    val manifest = manifestRoot.resolve("preloaded-downloads-v1.tsv")
    val oldCache = System.getProperty(BuildDependenciesConstants.DOWNLOAD_CACHE_DIR_PROPERTY)
    val oldManifest = System.getProperty(BuildDependenciesConstants.PRELOADED_DOWNLOADS_MANIFEST_PROPERTY)
    System.setProperty(BuildDependenciesConstants.DOWNLOAD_CACHE_DIR_PROPERTY, cache.toString())
    System.setProperty(BuildDependenciesConstants.PRELOADED_DOWNLOADS_MANIFEST_PROPERTY, manifest.toString())
    try {
      action(BuildDependenciesCommunityRoot(community), cache, manifestRoot)
    }
    finally {
      restoreProperty(BuildDependenciesConstants.DOWNLOAD_CACHE_DIR_PROPERTY, oldCache)
      restoreProperty(BuildDependenciesConstants.PRELOADED_DOWNLOADS_MANIFEST_PROPERTY, oldManifest)
      BuildDependenciesUtil.deleteFileOrFolder(root)
    }
  }

  private fun writeManifest(root: Path, name: String, sha256: String, url: String) {
    Files.writeString(root.resolve("preloaded-downloads-v1.tsv"), "intellij-build-downloads\t1\n$name\t$sha256\t$url\n")
  }

  private fun restoreProperty(name: String, value: String?) {
    if (value == null) {
      System.clearProperty(name)
    }
    else {
      System.setProperty(name, value)
    }
  }
}
