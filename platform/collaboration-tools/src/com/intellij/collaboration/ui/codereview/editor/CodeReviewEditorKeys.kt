// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.editor

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.editor.Editor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object CodeReviewEditorKeys {
  /**
   * Stores editor for cases when the current editor differs from the navigable one
   * (e.g., when focus is inside an in-editor review comment)
   */
  val NAVIGABLE_EDITOR_KEY: DataKey<Editor> = DataKey.create("Code.Review.Navigable.Editor")
}