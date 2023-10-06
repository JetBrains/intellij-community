// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide

import com.intellij.lang.documentation.ide.impl.DefaultDocumentationCustomization
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface DocumentationCustomization {
  val isAutoShowOnLookupItemChange: Boolean
  val autoShowDelayMillis: Long

  /** Whether to show the horizontal toolbar at the top of the documentation popup or gear icon in the bottom right corner */
  val isShowToolbar: Boolean

  fun editToolbarActions(group: DefaultActionGroup): ActionGroup {
    return group
  }

  fun editGearActions(group: DefaultActionGroup): ActionGroup {
    return group
  }

  fun isAvailable(editor: Editor): Boolean

  companion object {
    val EP_NAME: ExtensionPointName<DocumentationCustomization> = ExtensionPointName("com.intellij.lang.documentation.ide.customization")

    fun getForEditor(editor: Editor): DocumentationCustomization {
      return EP_NAME.findFirstSafe { it.isAvailable(editor) } ?: DefaultDocumentationCustomization
    }
  }
}