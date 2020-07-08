// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.markup

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName

interface InspectionWidgetActionProvider {
  companion object {
    @JvmField
      val EP_NAME: ExtensionPointName<InspectionWidgetActionProvider> = ExtensionPointName.create("com.intellij.iw.actionProvider")
  }

  /**
   * Creates action for the given editor.
   * User may return ActionGroup containing several actions and separators if needed.
   * All groups will be flattened upon adding to the inspection widget toolbar.
   */
  fun createAction(editor: Editor): AnAction
}