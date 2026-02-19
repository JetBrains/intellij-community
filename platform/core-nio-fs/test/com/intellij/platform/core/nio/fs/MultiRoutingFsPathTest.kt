// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.net.URI
import java.nio.file.FileSystems
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

class MultiRoutingFsPathTest {
  @Test
  fun `toAbsolutePath returns the same instance if isAbsolute`() {
    val fs = MultiRoutingFileSystemProvider(FileSystems.getDefault().provider()).getFileSystem(URI("file:/"))
    val sampleAbsolutePath = fs.rootDirectories.first().listDirectoryEntries().first()

    sampleAbsolutePath.isAbsolute.shouldBe(true)
    (sampleAbsolutePath.toAbsolutePath() === sampleAbsolutePath).shouldBe(true)
  }

  @Nested
  inner class `everything must return MultiRoutingFsPath` {
    val fs = MultiRoutingFileSystemProvider(defaultSunNioFs.provider()).getFileSystem(URI("file:/"))
    val rootDirectory = fs.rootDirectories.first()
    val childOfRootDirectory = rootDirectory.listDirectoryEntries().first()

    @Test
    fun toAbsolutePath() {
      rootDirectory.toAbsolutePath().shouldBeInstanceOf<MultiRoutingFsPath>()
      childOfRootDirectory.toAbsolutePath().shouldBeInstanceOf<MultiRoutingFsPath>()
    }

    @Test
    fun fileName() {
      childOfRootDirectory.fileName.shouldBeInstanceOf<MultiRoutingFsPath>()
    }

    @Test
    fun getName() {
      repeat(childOfRootDirectory.nameCount) { nameIdx ->
        childOfRootDirectory.getName(nameIdx).shouldBeInstanceOf<MultiRoutingFsPath>()
      }
    }

    @Test
    fun getRoot() {
      rootDirectory.root.shouldBeInstanceOf<MultiRoutingFsPath>()
      childOfRootDirectory.root.shouldBeInstanceOf<MultiRoutingFsPath>()
    }

    @Test
    fun normalize() {
      rootDirectory.normalize().shouldBeInstanceOf<MultiRoutingFsPath>()
      childOfRootDirectory.normalize().shouldBeInstanceOf<MultiRoutingFsPath>()
    }

    @Test
    fun relativize() {
      rootDirectory.relativize(childOfRootDirectory).shouldBeInstanceOf<MultiRoutingFsPath>()
      childOfRootDirectory.relativize(rootDirectory).shouldBeInstanceOf<MultiRoutingFsPath>()
    }

    @Test
    fun getParent() {
      childOfRootDirectory.parent.shouldBeInstanceOf<MultiRoutingFsPath>()
    }

    @Test
    fun resolve() {
      rootDirectory.resolve(childOfRootDirectory.fileName.toString()).shouldBeInstanceOf<MultiRoutingFsPath>()
      rootDirectory.resolve(childOfRootDirectory.fileName).shouldBeInstanceOf<MultiRoutingFsPath>()
      childOfRootDirectory.resolve("..").shouldBeInstanceOf<MultiRoutingFsPath>()
    }

    @Test
    fun resolveSibling() {
      childOfRootDirectory.resolveSibling(childOfRootDirectory.name.toString()).shouldBeInstanceOf<MultiRoutingFsPath>()
      childOfRootDirectory.resolveSibling(childOfRootDirectory.name).shouldBeInstanceOf<MultiRoutingFsPath>()
    }

    @Test
    fun subpath() {
      childOfRootDirectory.subpath(0, 1).shouldBeInstanceOf<MultiRoutingFsPath>()
    }

    @Test
    fun toRealPath() {
      rootDirectory.toRealPath().shouldBeInstanceOf<MultiRoutingFsPath>()
      childOfRootDirectory.toRealPath().shouldBeInstanceOf<MultiRoutingFsPath>()
    }
  }
}