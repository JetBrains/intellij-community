// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore.lang

import com.intellij.icons.AllIcons
import com.intellij.lang.LangBundle
import com.intellij.openapi.fileTypes.LanguageFileType
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * The file type of a `.analysisignore` file. The registration binds it to that exact file name.
 */
@ApiStatus.Internal
object AnalysisIgnoreFileType : LanguageFileType(AnalysisIgnoreLanguage) {
  override fun getName(): String = "AnalysisIgnore"

  override fun getDescription(): String = LangBundle.message("filetype.analysisignore.description")

  override fun getDefaultExtension(): String = "analysisignore"

  override fun getIcon(): Icon = AllIcons.Vcs.Ignore_file
}
