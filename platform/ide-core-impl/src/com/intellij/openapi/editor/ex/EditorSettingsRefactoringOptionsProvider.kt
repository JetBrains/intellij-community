// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service

/**
 * Represents a view on UI settings stored within the editor settings.
 */
open class EditorSettingsRefactoringOptionsProvider {
  /**
   * @return true if the dialog could be shown during inline refactorings.
   */
  open fun isShowInlineDialog(): Boolean {
    return EditorSettingsExternalizable.getInstance().isShowInlineLocalDialog
  }

  /**
   * Sets the option whether the dialog could be shown during inline refactorings.
   */
  open fun setShowInlineDialog(value: Boolean) {
    EditorSettingsExternalizable.getInstance().isShowInlineLocalDialog = value
  }

  companion object {
    @JvmStatic
    fun getInstance(): EditorSettingsRefactoringOptionsProvider {
      return ApplicationManager.getApplication().service<EditorSettingsRefactoringOptionsProvider>()
    }
  }
}