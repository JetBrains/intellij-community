// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus

/**
 * Represents a view on UI settings stored within the editor settings.
 */
@ApiStatus.Internal
interface EditorSettingsRefactoringOptionsProvider {
  /**
   * Controls whether dialog could be shown during inline local refactoring.
   */
  var isShowInlineLocalDialog: Boolean

  companion object {
    @JvmStatic
    fun getInstance(): EditorSettingsRefactoringOptionsProvider {
      return service<EditorSettingsRefactoringOptionsProvider>()
    }
  }
}