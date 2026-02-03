// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.specialPaths

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
data class SpecialPathEntry(val name: String, val originalPath: String, val kind: Kind) {
  enum class Kind {
    File,
    Folder
  }

  val path: Path? = try {
    Path.of(FileUtil.toSystemDependentName(originalPath))
  } catch (_: Throwable) {
    null
  }
}
