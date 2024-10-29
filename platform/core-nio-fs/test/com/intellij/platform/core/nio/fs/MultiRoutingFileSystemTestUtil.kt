// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs

import java.nio.file.FileSystem
import java.nio.file.FileSystems

internal val defaultSunNioFs: FileSystem by lazy {
  val fs = FileSystems.getDefault()
  val provider = fs.provider()
  if (provider.javaClass.name == MultiRoutingFileSystemProvider::class.java.name) {
    provider.javaClass.getDeclaredField("myLocalProvider").get(provider) as FileSystem
  }
  else {
    fs
  }
}