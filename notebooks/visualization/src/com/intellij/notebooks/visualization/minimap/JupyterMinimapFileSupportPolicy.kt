// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.minimap

import com.intellij.ide.minimap.model.MinimapFileSupportPolicy
import com.intellij.ide.minimap.model.MinimapSupportLevel
import com.intellij.notebooks.jupyter.core.jupyter.JupyterFileType
import com.intellij.openapi.fileTypes.FileType

internal class JupyterMinimapFileSupportPolicy : MinimapFileSupportPolicy {
  override fun getSupportLevel(fileType: FileType): MinimapSupportLevel? {
    return if (fileType is JupyterFileType) MinimapSupportLevel.UNSUPPORTED else null
  }
}
