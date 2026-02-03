// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS.WINDOWS
import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries

class MultiRoutingFileSystemTest {
  @Nested
  inner class `everything must return MultiRoutingFsPath` {
    val fs = MultiRoutingFileSystemProvider(defaultSunNioFs.provider()).getFileSystem(URI("file:/"))

    @Test
    fun rootDirectories() {
      for (path in fs.rootDirectories) {
        withClue(path.toString()) {
          path.shouldBeInstanceOf<MultiRoutingFsPath>()
        }
      }
    }

    @Test
    fun getPath() {
      val stringPath = fs.rootDirectories.first().listDirectoryEntries().first().toString()

      fs.getPath(stringPath).shouldBeInstanceOf<MultiRoutingFsPath>()
      fs.getPath("").shouldBeInstanceOf<MultiRoutingFsPath>()
      fs.getPath(".").shouldBeInstanceOf<MultiRoutingFsPath>()
      fs.getPath("..").shouldBeInstanceOf<MultiRoutingFsPath>()
    }
  }

  @Nested
  @EnabledOnOs(WINDOWS)
  inner class `WSL backends` {
    val fs = MultiRoutingFileSystemProvider(defaultSunNioFs.provider()).getFileSystem(URI("file:/"))
    val wslFs = mockk<FileSystem>()

    init {
      val root = "//wsl.localhost/ubuntu-22.04"

      fs.setBackendProvider(
        { localFs, sanitizedPath ->
          if (sanitizedPath.lowercase().startsWith(root)) wslFs
          else localFs
        },
        { localFs ->
          listOf(localFs.getPath(root))
        },
        {
          TODO("Not yet implemented")
        },
      )
    }

    @Test
    fun `match by root`() {
      fs.getBackend("\\\\wsl.localhost\\Ubuntu-22.04") shouldBe wslFs
    }

    @Test
    fun `unix slash`() {
      fs.getBackend("//wsl.localhost/Ubuntu-22.04") shouldBe wslFs
    }

    @Test
    fun `c drive`() {
      fs.getBackend("C:") shouldBe defaultSunNioFs
      fs.getBackend("C:\\") shouldBe defaultSunNioFs
      fs.getBackend("C:\\Users") shouldBe defaultSunNioFs
    }

    @Test
    fun `windows path is not routable`() {
      fs.isRoutable(Path.of("C:\\")) shouldBe false
    }

    @Test
    fun `wsl path is routable`() {
      fs.isRoutable(Path.of("\\\\wsl.localhost\\Ubuntu-22.04\\")) shouldBe true
    }
  }
}