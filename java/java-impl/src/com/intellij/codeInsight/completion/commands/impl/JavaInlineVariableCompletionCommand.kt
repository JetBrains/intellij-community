// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.command.commands.AbstractInlineVariableCompletionCommandProvider
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.java.JavaBundle
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ui.EDT
import org.jetbrains.annotations.Nls

internal class JavaInlineVariableCompletionCommandProvider : AbstractInlineVariableCompletionCommandProvider() {
  override val presentableName: @Nls String
    get() = JavaBundle.message("command.completion.inline.text")

  override fun findElementToInline(offset: Int, psiFile: PsiFile, editor: Editor?): PsiElement? {
    val element = getCommandContext(offset, psiFile) ?: return null
    if (element !is PsiIdentifier) return null
    val javaRef = PsiTreeUtil.getParentOfType(element, PsiJavaCodeReferenceElement::class.java) ?: return null
    val onEdt = EDT.isCurrentThreadEdt()
    fun doResolve(): PsiElement? = javaRef.resolve()
    val psiElement = if (onEdt) {
      runWithModalProgressBlocking(ModalTaskOwner.guess(), JavaBundle.message("command.completion.inline.text"))
      { readAction { doResolve() } }
    }
    else {
      doResolve()
    }
    if (psiElement !is PsiVariable) return null
    if (psiElement is PsiField && psiElement.initializer == null) return null
    if (psiElement is PsiParameter) return null
    return psiElement
  }
}