// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * Defines a provider for creating instances of {@link AbstractIntentionPopup}.
 * This interface is intended to provide a customized intention popup that can include intention from different backends.
 */
public interface IntentionPopupProvider {
  companion object {
    val EP_NAME = ExtensionPointName.create<IntentionPopupProvider>("com.intellij.intentionPopupProvider")

    /**
     * Creates custom instance of {@link AbstractIntentionPopup}
     *
     * @param editor The editor where the popup is to be displayed.
     * @param file The file currently being edited.
     * @param project The current project.
     * @return A custom instance of {@link AbstractIntentionPopup}, or {@code null} if default popup
     * implementation should be used
     */
    fun createPopup(editor: Editor, file: PsiFile, project: Project): AbstractIntentionPopup? {
      return EP_NAME.extensionList.firstNotNullOfOrNull { it.createPopup(editor, file, project) }
    }
  }

  fun createPopup(editor: Editor, file: PsiFile, project: Project): AbstractIntentionPopup?
}