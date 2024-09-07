// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ai

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus.Experimental

/**
 * Manages the inline prompts shown in editors.
 * An inline prompt is a piece of natural language text written right in the editor itself.
 * When we detect it, we start an inline prompt editing mode.
 * When we edit inline prompt, programming language features should not interfere with the prompt
 */
@Experimental
interface InlinePromptManager {
  companion object {
    fun getInstance(project: Project): InlinePromptManager = project.getService(InlinePromptManager::class.java)
  }

  /**
   * Checks if the inline prompt is currently shown in the specified editor.
   *
   * @param editor the editor in which the inline prompt visibility is to be checked
   * @return true if the inline prompt is shown, false otherwise
   */
  fun isInlinePromptShown(editor: Editor): Boolean
}