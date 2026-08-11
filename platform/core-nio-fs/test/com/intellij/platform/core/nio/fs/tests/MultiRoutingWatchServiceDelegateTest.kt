// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs.tests

import com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
import com.intellij.platform.core.nio.fs.MultiRoutingFsPath
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.nio.file.ClosedWatchServiceException
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.util.concurrent.TimeUnit
import kotlin.io.path.createFile
import kotlin.io.path.name

class MultiRoutingWatchServiceDelegateTest {
  private companion object {
    const val WRAPPED_WATCH_KEY_CLASS_NAME = "com.intellij.platform.core.nio.fs.MultiRoutingWatchKeyDelegate"
  }

  @Test
  fun `cancel and re-register preserves key identity`(@TempDir tempDir: Path) {
    val fs = MultiRoutingFileSystemProvider(defaultSunNioFs.provider()).getFileSystem(URI("file:/"))
    val watchedDirectory = fs.getPath(tempDir.toString())

    fs.newWatchService().use { watchService ->
      // Register, cancel, re-register
      val firstKey = watchedDirectory.register(watchService, ENTRY_CREATE)
      firstKey.cancel()

      val secondKey = watchedDirectory.register(watchService, ENTRY_CREATE)
      secondKey.javaClass.name.shouldBe(WRAPPED_WATCH_KEY_CLASS_NAME)

      // Create a file and verify the new key is returned by poll()
      watchedDirectory.resolve("file.txt").createFile()
      val observedKey = requireNotNull(watchService.poll(10, TimeUnit.SECONDS))
      assertSame(secondKey, observedKey)
    }
  }

  @Test
  fun `register returns the same key instance as the key observed from the watch service`(@TempDir tempDir: Path) {
    val fs = MultiRoutingFileSystemProvider(defaultSunNioFs.provider()).getFileSystem(URI("file:/"))
    val watchedDirectory = fs.getPath(tempDir.toString())
    val firstCreatedFile = watchedDirectory.resolve("created.txt")
    val secondCreatedFile = watchedDirectory.resolve("created-again.txt")

    fs.newWatchService().use { watchService ->
      val registeredKey = watchedDirectory.register(watchService, ENTRY_CREATE)
      val registeredWatchable = registeredKey.watchable().shouldBeInstanceOf<MultiRoutingFsPath>()

      registeredKey.javaClass.name.shouldBe(WRAPPED_WATCH_KEY_CLASS_NAME)
      registeredWatchable.toString().shouldBe(watchedDirectory.toString())
      watchService.poll().shouldBe(null)

      firstCreatedFile.createFile()

      val observedKey = requireNotNull(watchService.poll(10, TimeUnit.SECONDS))
      val observedWatchable = observedKey.watchable().shouldBeInstanceOf<MultiRoutingFsPath>()

      observedKey.javaClass.name.shouldBe(WRAPPED_WATCH_KEY_CLASS_NAME)
      assertSame(registeredKey, observedKey)
      observedKey.shouldBe(registeredKey)
      observedKey.hashCode().shouldBe(registeredKey.hashCode())
      observedWatchable.toString().shouldBe(watchedDirectory.toString())

      val firstCreateEvent = observedKey.pollEvents().first { it.kind() == ENTRY_CREATE }
      firstCreateEvent.context().shouldBeInstanceOf<MultiRoutingFsPath>().name.shouldBe(firstCreatedFile.name)
      observedKey.reset().shouldBe(true)

      watchService.poll().shouldBe(null)
      secondCreatedFile.createFile()

      val observedKeyAgain = requireNotNull(watchService.poll(10, TimeUnit.SECONDS))
      assertSame(registeredKey, observedKeyAgain)
      observedKeyAgain.pollEvents().first { it.kind() == ENTRY_CREATE }
        .context().shouldBeInstanceOf<MultiRoutingFsPath>().name.shouldBe(secondCreatedFile.name)
      observedKeyAgain.reset().shouldBe(true)
    }
  }

  @Test
  fun `close unblocks take and throws ClosedWatchServiceException`(@TempDir tempDir: Path) {
    val fs = MultiRoutingFileSystemProvider(defaultSunNioFs.provider()).getFileSystem(URI("file:/"))
    val watchService = fs.newWatchService()
    val watchedDirectory = fs.getPath(tempDir.toString())
    watchedDirectory.register(watchService, ENTRY_CREATE)

    val thread = Thread {
      try {
        watchService.take()
        throw AssertionError("Expected ClosedWatchServiceException")
      }
      catch (_: ClosedWatchServiceException) {
        // expected
      }
    }
    thread.start()
    Thread.sleep(200) // let the thread block on take()
    watchService.close()
    thread.join(5000)
    thread.isAlive.shouldBe(false)
  }

  @Test
  fun `poll returns null when no events available`(@TempDir tempDir: Path) {
    val fs = MultiRoutingFileSystemProvider(defaultSunNioFs.provider()).getFileSystem(URI("file:/"))
    val watchedDirectory = fs.getPath(tempDir.toString())

    fs.newWatchService().use { watchService ->
      watchedDirectory.register(watchService, ENTRY_CREATE)
      watchService.poll().shouldBe(null)
      watchService.poll(100, TimeUnit.MILLISECONDS).shouldBe(null)
    }
  }
}
