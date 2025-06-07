// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs

import com.intellij.util.containers.forEachGuaranteed
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldNotBeInstanceOf
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ThreadLocalRandom
import java.util.zip.ZipOutputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.outputStream

class MultiRoutingFileSystemProviderTest {

  private fun withEmptyZipFile(f: (Path) -> Unit) {
    val emptyZip = createTempFile("empty-zip", ".zip")
    ZipOutputStream(emptyZip.outputStream()).use { }
    try {
      f(emptyZip)
    }
    finally {
      emptyZip.deleteIfExists()
    }
  }

  @Test
  fun `zip file system can be created`() {
    val provider = MultiRoutingFileSystemProvider(defaultSunNioFs.provider())
    provider.getPath(defaultSunNioFs.rootDirectories.first().toUri().resolve("file.zip")).shouldBeInstanceOf<MultiRoutingFsPath>()
    shouldThrow<UnsupportedOperationException> {
      provider.getFileSystem(defaultSunNioFs.rootDirectories.first().toUri().resolve("file.zip"))
    }
    withEmptyZipFile { emptyZip ->
      FileSystems.newFileSystem(emptyZip).shouldNotBeInstanceOf<MultiRoutingFileSystem>()
      FileSystems.newFileSystem(emptyZip).rootDirectories.shouldBeSingleton().single().listDirectoryEntries().shouldBeEmpty()
    }
  }

  @Test
  fun `probe content type`() {
    withEmptyZipFile { path ->
      val provider = MultiRoutingFileSystemProvider(defaultSunNioFs.provider())
      val wrappedPath = provider.getPath(path.toUri())
      wrappedPath.shouldBeInstanceOf<MultiRoutingFsPath>()
      Files.probeContentType(wrappedPath).shouldBe("application/zip")
    }
  }

  @Nested
  inner class `everything must return MultiRoutingFsPath` {
    val provider = MultiRoutingFileSystemProvider(defaultSunNioFs.provider())
    val rootDirectory = provider.getFileSystem(URI("file:/")).rootDirectories.first()

    @Test
    fun `getPath of URI`() {
      provider.getPath(defaultSunNioFs.rootDirectories.first().toUri()).shouldBeInstanceOf<MultiRoutingFsPath>()
    }

    @Test
    fun newDirectoryStream() {
      provider.newDirectoryStream(rootDirectory, { true }).use { pathIter ->
        for (path in pathIter) {
          withClue(path.toString()) {
            path.shouldBeInstanceOf<MultiRoutingFsPath>()
          }
        }
      }
    }

    @Test
    fun newDirectoryStreamWithFilter() {
      provider.newDirectoryStream(rootDirectory, { path ->
        withClue(path.toString()) {
          path.shouldBeInstanceOf<MultiRoutingFsPath>()
        }
        true
      }).use { pathIter ->
        for (path in pathIter) {
          withClue(path.toString()) {
            path.shouldBeInstanceOf<MultiRoutingFsPath>()
          }
        }
      }
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun readSymbolicLink(): Unit = CloseableList().use { closeables ->
      val targetDefaultFs = Files.createTempFile("MultiRoutingFileSystemProviderTest-target", ".txt")

      closeables.add {
        Files.delete(targetDefaultFs)
      }

      val randomString = BigInteger(ByteArray(10).also(ThreadLocalRandom.current()::nextBytes)).abs().toString(36)

      val linkDefaultFs =
        try {
          Files.createSymbolicLink(targetDefaultFs.parent.resolve("MultiRoutingFileSystemProviderTest-link-$randomString"), targetDefaultFs)
        }
        catch (_: Throwable) {
          // TODO Class-loader tricks are suspected in failing tests on CI.
          //  UnsupportedOperationException + FileSystemException should be caught instead of Throwable.
          Assumptions.abort("This OS does not support symbolic links")
        }

      closeables.add {
        Files.delete(linkDefaultFs)
      }

      val linkMrfsp = provider.getPath(linkDefaultFs.toUri())
      linkMrfsp.shouldBeInstanceOf<MultiRoutingFsPath>()
    }
  }

  private class CloseableList : MutableList<AutoCloseable> by mutableListOf(), AutoCloseable {
    override fun close() {
      asReversed().forEachGuaranteed { it.close() }
    }
  }
}