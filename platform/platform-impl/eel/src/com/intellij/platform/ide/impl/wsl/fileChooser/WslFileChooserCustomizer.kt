// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.wsl.fileChooser

import com.intellij.openapi.fileChooser.FileChooserCustomizer
import com.intellij.openapi.fileChooser.FileChooserCustomizer.getFileIconByName
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.ide.impl.wsl.WslEelDescriptor
import java.nio.file.Path
import javax.swing.Icon

internal class WslFileChooserCustomizer : FileChooserCustomizer {

  override fun fastGetIcon(project: Project?, filePath: Path): Icon? {
    return if (filePath.toString().startsWith("\\\\wsl") && filePath.getEelDescriptor() is WslEelDescriptor) {
      getFileIconByName(filePath)
    }
    else null
  }

}
