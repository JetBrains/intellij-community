// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs

import com.intellij.util.containers.forEachGuaranteed
import io.kotest.assertions.withClue
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.ThreadLocalRandom
import kotlin.io.path.ExperimentalPathApi

class MultiRoutingFileSystemProviderTest {
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