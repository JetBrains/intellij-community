// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.eel.isWindows
import com.intellij.util.io.ArchiveBackend
import java.nio.file.Path


internal class ArchiveBackendImpl : ArchiveBackend {
  override fun isWindows(path: Path): Boolean =
    if (ApplicationManager.getApplication() == null) {
      // If app isn't loaded are called from some low-level thing and can't access eel without app anyway
      SystemInfo.isWindows
    }
    else {
      path.getEelDescriptor().osFamily.isWindows
    }
}