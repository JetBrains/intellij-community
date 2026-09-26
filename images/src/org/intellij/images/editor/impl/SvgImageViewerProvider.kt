// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.editor.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.vfs.VirtualFile

interface SvgImageViewerProvider {
  fun createEditor(file: VirtualFile): FileEditor?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<SvgImageViewerProvider> = ExtensionPointName.create("org.intellij.images.svgImageViewerProvider")

    @JvmStatic
    fun createEditorFromExtensions(file: VirtualFile): FileEditor? {
      return EP_NAME.extensionList.firstNotNullOfOrNull { it.createEditor(file) }
    }
  }
}
