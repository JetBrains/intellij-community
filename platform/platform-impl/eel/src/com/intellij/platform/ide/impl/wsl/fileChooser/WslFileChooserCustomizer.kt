// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.wsl.fileChooser

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileChooser.FileChooserCustomizer
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.ide.impl.wsl.WslEelDescriptor
import java.nio.file.Path
import javax.swing.Icon
import kotlin.io.path.name

internal class WslFileChooserCustomizer : FileChooserCustomizer {

  override fun fastGetIcon(project: Project?, filePath: Path): Icon? {
    return if (filePath.toString().startsWith("\\\\wsl") && filePath.getEelDescriptor() is WslEelDescriptor) {
      val fileType = FileTypeManager.getInstance().getFileTypeByFileName(filePath.name)
      if (fileType is UnknownFileType) AllIcons.Empty else fileType.icon ?: AllIcons.Empty
    }
    else null
  }

}
