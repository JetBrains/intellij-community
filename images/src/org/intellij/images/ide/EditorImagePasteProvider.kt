// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.images.ide

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile

/**
 * Represents a paste provider that allows to paste screenshots (from clipboard) as PNG files and insert links to currently edited document.
 *
 * NOTE: If registered as `customPasteProvider` handles paste operations in editor.
 */
abstract class EditorImagePasteProvider : ImagePasteProvider() {
  abstract val supportedFileType: FileType

  final override fun isEnabledForDataContext(dataContext: DataContext): Boolean =
    dataContext.getData(CommonDataKeys.EDITOR) != null &&
    dataContext.getData(CommonDataKeys.VIRTUAL_FILE)?.fileType == supportedFileType

  final override fun imageFilePasted(dataContext: DataContext, imageFile: VirtualFile) {
    dataContext.getData(CommonDataKeys.EDITOR)?.imageFilePasted(imageFile)
  }

  abstract fun Editor.imageFilePasted(imageFile: VirtualFile)
}