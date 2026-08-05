// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.provider

import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.provider.osFamily
import com.intellij.util.io.ArchiveBackend
import java.nio.file.Path

internal class ArchiveBackendImpl : ArchiveBackend() {
  override fun isWindows(path: Path): Boolean = path.osFamily.isWindows
}
