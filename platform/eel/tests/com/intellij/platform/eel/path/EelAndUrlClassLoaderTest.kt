// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.path

import com.intellij.util.lang.UrlClassLoader
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.FileSystems

class EelAndUrlClassLoaderTest {
  @Test
  fun `getFiles returns paths from the default file system`() {
    FileSystems.getDefault().javaClass.name shouldBe "com.intellij.platform.core.nio.fs.MultiRoutingFileSystem"
    val classLoader = javaClass.classLoader as UrlClassLoader

    for (path in classLoader.files) {
      shouldNotThrowAny {
        path.toFile()
      }
    }
  }
}