// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation.actions

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.navigation.CtrlMouseData
import com.intellij.codeInsight.navigation.CtrlMouseInfo
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

class GotoDeclarationOnlyAction : GotoDeclarationAction() {

  override fun getHandler(): CodeInsightActionHandler {
    return GotoDeclarationOnlyHandler2
  }

  override fun getCtrlMouseInfo(editor: Editor, file: PsiFile, offset: Int): CtrlMouseInfo? {
    return GotoDeclarationOnlyHandler2.getCtrlMouseInfo(editor, file, offset)
  }

  override fun getCtrlMouseData(editor: Editor, file: PsiFile, offset: Int): CtrlMouseData? {
    return GotoDeclarationOnlyHandler2.getCtrlMouseData(editor, file, offset)
  }
}
