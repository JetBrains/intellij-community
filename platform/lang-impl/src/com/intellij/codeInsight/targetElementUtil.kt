// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight

import com.intellij.codeInsight.TargetElementUtil.adjustOffset
import com.intellij.codeInsight.navigation.GotoDeclarationProvider.collectAllTargets
import com.intellij.injected.editor.EditorWindow
import com.intellij.model.SymbolReference
import com.intellij.navigation.NavigationTarget
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

typealias AdjustedOffset = Int

fun findReferences(editor: Editor, file: PsiFile): Collection<SymbolReference> = file.findAllReferencesAt(adjustedOffset(editor, file))

fun adjustedOffset(editor: Editor, file: PsiFile): AdjustedOffset = adjustOffset(file, editor.document, editor.caretModel.offset)

fun findAllTargets(project: Project, editor: Editor, file: PsiFile): Collection<NavigationTarget> {
  val offset = editor.caretModel.offset
  if (TargetElementUtil.inVirtualSpace(editor, offset)) {
    return emptyList()
  }
  return findAllTargetsNoVS(project, editor, offset, file)
}

private fun findAllTargetsNoVS(project: Project, editor: Editor, offset: Int, file: PsiFile): Collection<NavigationTarget> {
  val targets = collectAllTargets(project, editor, file)
  if (!targets.isEmpty()) {
    return targets
  }
  // if no targets found in injected fragment, try outer document
  if (editor is EditorWindow) {
    return findAllTargetsNoVS(project, editor.delegate, editor.document.injectedToHost(offset), file)
  }
  return emptyList()
}
