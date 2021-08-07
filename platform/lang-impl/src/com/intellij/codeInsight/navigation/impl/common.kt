// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation.impl

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.ide.util.EditSourceUtil
import com.intellij.injected.editor.EditorWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.navigation.EmptyNavigatable
import com.intellij.openapi.editor.Editor
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

internal fun <X : Any> processInjectionThenHost(file: PsiFile, offset: Int, function: (file: PsiFile, offset: Int) -> X?): X? {
  return function(file, offset)
         ?: fromHostFile(file, offset, function)
}

private fun <X : Any> fromHostFile(file: PsiFile, offset: Int, function: (file: PsiFile, offset: Int) -> X?): X? {
  val manager = InjectedLanguageManager.getInstance(file.project)
  val topLevelFile = manager.getTopLevelFile(file) ?: return null
  return function(topLevelFile, manager.injectedToHost(file, offset))
}

internal fun <X : Any> processInjectionThenHost(editor: Editor, offset: Int, function: (editor: Editor, offset: Int) -> X?): X? {
  return function(editor, offset)
         ?: fromHostEditor(editor, offset, function)
}

private fun <X : Any> fromHostEditor(editor: Editor, offset: Int, function: (editor: Editor, offset: Int) -> X?): X? {
  if (editor !is EditorWindow) {
    return null
  }
  return function(editor.delegate, editor.document.injectedToHost(offset))
}

internal fun gtdTargetNavigatable(targetElement: PsiElement): Navigatable {
  val target = TargetElementUtil.getInstance().getGotoDeclarationTarget(targetElement, targetElement.navigationElement)
               ?: return EmptyNavigatable.INSTANCE
  return psiNavigatable(target)
}

internal fun psiNavigatable(targetElement: PsiElement): Navigatable {
  return targetElement as? Navigatable
         ?: EditSourceUtil.getDescriptor(targetElement)
         ?: EmptyNavigatable.INSTANCE
}
