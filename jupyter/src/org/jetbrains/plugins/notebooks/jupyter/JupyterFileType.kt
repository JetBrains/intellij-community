// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.notebooks.jupyter

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.VirtualFile
import icons.JupyterCoreIcons
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

object JupyterFileType : LanguageFileType(JupyterLanguage) {
  @NonNls
  override fun getName() = "Jupyter"
  override fun getDescription() = JupyterPsiBundle.message("filetype.jupyter.description")
  override fun getDefaultExtension() = "ipynb"
  override fun getIcon(): Icon = JupyterCoreIcons.JupyterNotebook
  override fun getCharset(file: VirtualFile, content: ByteArray): String = CharsetToolkit.UTF8
}