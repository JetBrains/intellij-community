// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.template

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

/** Maps Java elements to their template source files. */
@ApiStatus.Internal
interface JavaTemplateDebuggerSupport {
  fun getTemplateSourceFile(element: PsiElement): PsiFile? = null

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<JavaTemplateDebuggerSupport> =
      ExtensionPointName.create("com.intellij.java.templateDebuggerSupport")

    @JvmStatic
    fun findSourceFile(element: PsiElement): PsiFile? =
      EP_NAME.extensionList.firstNotNullOfOrNull { it.getTemplateSourceFile(element) } ?: element.containingFile
  }
}
