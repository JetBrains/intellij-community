// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs

import io.kotest.assertions.withClue
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.net.URI
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
}