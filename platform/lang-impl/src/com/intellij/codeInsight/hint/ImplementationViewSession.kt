// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hint

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.Processor

interface ImplementationViewSession {
  val project: Project
  val implementationElements: List<ImplementationViewElement>
  val file: PsiFile?

  val element: PsiElement?
  val text: String?
  val editor: Editor?

  fun createSessionForLookupElement(lookupItemObject: Any?, isSearchDeep: Boolean): PsiImplementationViewSession?

  fun searchImplementationsInBackground(indicator: ProgressIndicator,
                                        isSearchDeep: Boolean,
                                        includeSelf: Boolean,
                                        processor: Processor<PsiElement>): List<ImplementationViewElement>
  fun elementRequiresIncludeSelf(): Boolean
  fun needUpdateInBackground(): Boolean
}

interface ImplementationViewSessionFactory {
  fun createSession(dataContext: DataContext, project: Project, invokedByShortcut: Boolean): ImplementationViewSession?

  companion object {
    @JvmField val EP_NAME = ExtensionPointName.create<ImplementationViewSessionFactory>("com.intellij.implementationViewSessionFactory")
  }
}

class PsiImplementationSessionViewFactory : ImplementationViewSessionFactory {
  override fun createSession(dataContext: DataContext, project: Project, invokedByShortcut: Boolean): ImplementationViewSession? {
    return PsiImplementationViewSession.create(dataContext, project, invokedByShortcut)
  }
}
