// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

public interface IntentionPopupProvider {
  companion object {
    val EP_NAME = ExtensionPointName.create<IntentionPopupProvider>("com.intellij.intentionPopupProvider")

    fun createPopup(editor: Editor, file: PsiFile, project: Project): AbstractIntentionPopup? {
      return EP_NAME.extensionList.firstNotNullOfOrNull { it.createPopup(editor, file, project) }
    }
  }

  fun createPopup(editor: Editor, file: PsiFile, project: Project): AbstractIntentionPopup?
}