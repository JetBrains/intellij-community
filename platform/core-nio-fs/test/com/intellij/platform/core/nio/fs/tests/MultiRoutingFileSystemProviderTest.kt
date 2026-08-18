// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs.tests

import com.intellij.platform.core.nio.fs.MultiRoutingFileSystem
import com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
import com.intellij.platform.core.nio.fs.MultiRoutingFsPath
import com.intellij.util.containers.forEachGuaranteed
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldNotBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.READ
import java.nio.file.spi.FileSystemProvider
import java.util.concurrent.ThreadLocalRandom
import java.util.zip.ZipOutputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.outputStream
import kotlin.io.path.readText
import kotlin.io.path.writeText

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

  @Test
  fun `URI paths use the same backend as string paths`() {
    withEmptyZipFile { zip ->
      FileSystems.newFileSystem(zip).use { backend ->
        val provider = MultiRoutingFileSystemProvider(defaultSunNioFs.provider())
        val fs = provider.theOnlyFileSystem
        val localPath = defaultSunNioFs.getPath("routed path #1.txt").toAbsolutePath()
        val routedPath = MultiRoutingFileSystem.sanitizeRoot(localPath.toString())
        fs.setBackendProvider({ local, path -> if (path == routedPath) backend else local }, null, null)

        val actual = provider.getPath(localPath.toUri())
        assertSame(backend, actual.initialDelegate.fileSystem)
        assertEquals(fs.getPath(localPath.toString()), actual)
      }
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

  @Nested
  inner class `open options unknown to the local provider` {
    val provider = MultiRoutingFileSystemProvider(defaultSunNioFs.provider())
    val unknownOption = object : OpenOption {}

    @AfterEach
    fun tearDown() {
      unmockkAll()
    }

    @Test
    fun `local path drops the option`() {
      val tempDir = defaultSunNioFs.getPath(System.getProperty("java.io.tmpdir"))
      val file = createTempFile(tempDir, "MultiRoutingFileSystemProviderTest", ".txt")
      try {
        file.writeText("hello")
        shouldThrow<UnsupportedOperationException> {
          defaultSunNioFs.provider().newByteChannel(file, setOf(READ, unknownOption))
        }

        val path = provider.getPath(file.toUri())
        provider.newInputStream(path, unknownOption).use { it.readAllBytes().decodeToString() shouldBe "hello" }
        provider.newByteChannel(path, setOf(READ, unknownOption)).close()
        provider.newFileChannel(path, setOf(READ, unknownOption)).close()
        provider.newAsynchronousFileChannel(path, setOf(READ, unknownOption), null).close()
        provider.newOutputStream(path, APPEND, unknownOption).use { it.write(" world".toByteArray()) }
        file.readText() shouldBe "hello world"
      }
      finally {
        file.deleteIfExists()
      }
    }

    @Test
    fun `routed path keeps the option`() {
      val backendProvider = mockk<FileSystemProvider>(relaxed = true)
      val backend = mockk<FileSystem>(relaxed = true) {
        every { provider() } returns backendProvider
        every { getPath(any<String>(), *anyVararg<String>()) } answers { defaultSunNioFs.getPath(firstArg<String>()) }
      }
      val localPath = defaultSunNioFs.getPath("routed.txt").toAbsolutePath()
      val routedPath = MultiRoutingFileSystem.sanitizeRoot(localPath.toString())
      provider.theOnlyFileSystem.setBackendProvider({ local, path -> if (path == routedPath) backend else local }, null, null)

      val options = setOf(READ, unknownOption)
      provider.newByteChannel(provider.theOnlyFileSystem.getPath(localPath.toString()), options)
      verify { backendProvider.newByteChannel(any(), options, *anyVararg()) }
    }
  }

  private class CloseableList : MutableList<AutoCloseable> by mutableListOf(), AutoCloseable {
    override fun close() {
      asReversed().forEachGuaranteed { it.close() }
    }
  }
}
