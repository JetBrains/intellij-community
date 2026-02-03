// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider

import com.intellij.platform.eel.channels.EelDelicateApi
import com.intellij.platform.eel.isWindows
import com.intellij.util.io.ArchiveBackend
import java.nio.file.Path

internal class ArchiveBackendImpl : ArchiveBackend {
  @OptIn(EelDelicateApi::class)
  override fun isWindows(path: Path): Boolean =
    // If app isn't loaded we are called from some low-level thing and can't access eel without app anyway
    path.osFamily.isWindows
}