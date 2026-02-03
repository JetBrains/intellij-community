// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.environment

import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths

internal abstract class ExternalEditorCollectionDataProvider {
  protected val homeDirectory: Path? = try {
    Paths.get(System.getProperty("user.home"))
  }
  catch (_: InvalidPathException) {
    null
  }
}