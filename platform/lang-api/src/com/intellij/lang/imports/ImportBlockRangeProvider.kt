// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.imports

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

interface ImportBlockRangeProvider {
  companion object {
    @JvmStatic
    val EP_NAME: ExtensionPointName<ImportBlockRangeProvider> = ExtensionPointName.create("com.intellij.importBlockRangeProvider")

    @JvmStatic
    fun getRange(file: PsiFile): TextRange? = EP_NAME.extensionList.firstNotNullOfOrNull { it.getImportBlockRange(file) }

  }

  fun getImportBlockRange(file: PsiFile): TextRange?
}