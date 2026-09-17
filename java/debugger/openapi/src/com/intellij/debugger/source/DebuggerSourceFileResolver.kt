// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.source

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

/** Resolves debugger source files and their presentation. */
@ApiStatus.Internal
interface DebuggerSourceFileResolver {
  fun resolveSourceFile(element: PsiElement): PsiFile? = null

  fun isTemplateSource(file: PsiFile): Boolean = false

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<DebuggerSourceFileResolver> =
      ExtensionPointName.create("com.intellij.debugger.sourceFileResolver")

    @JvmStatic
    fun findSourceFile(element: PsiElement): PsiFile? =
      EP_NAME.extensionList.firstNotNullOfOrNull { it.resolveSourceFile(element) } ?: element.containingFile

    @JvmStatic
    fun isTemplateSourceFile(file: PsiFile): Boolean = EP_NAME.extensionList.any { it.isTemplateSource(file) }
  }
}
