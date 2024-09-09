// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("InlinePrompt")

package com.intellij.inlinePrompt

import com.intellij.inlinePrompt.InlinePromptManager.Companion.getInstance
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.Icon

/**
 * Manages the inline prompts shown in editors.
 * An inline prompt is a piece of natural language text written right in the editor itself.
 * When we detect it, we start an inline prompt editing mode.
 * When we edit inline prompt, programming language features should not interfere with the prompt
 */
@Internal
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

  /**
   * Checks if inline prompt code is currently being generated in the specified editor.
   *
   * @param editor the editor in which the code generation status of the inline prompt is to be checked
   * @return true if inline prompt code is being generated, false otherwise
   */
  fun isInlinePromptCodeGenerating(editor: Editor): Boolean

  fun getBulbIcon(): Icon?
}

/**
 * Checks if the inline prompt is currently shown in the specified editor.
 *
 * @param editor the editor in which the inline prompt visibility is to be checked
 * @param project the project associated with the editor, defaults to the project of the editor if not provided
 * @return true if the inline prompt is shown, false otherwise
 */
@Experimental
@JvmOverloads
fun isInlinePromptShown(editor: Editor, project: Project? = editor.project): Boolean {
  if (project == null) return false
  return getInstance(project).isInlinePromptShown(editor)
}

/**
 * @return the inline prompt bulb icon if an inline prompt is currently shown or is being generated in the specified editor.
 */
@Internal
fun getInlinePromptBulbIcon(project: Project, editor: Editor): Icon? {
  val inlinePromptManager = getInstance(project)
  val isInlinePrompt = inlinePromptManager.isInlinePromptShown(editor) || inlinePromptManager.isInlinePromptCodeGenerating(editor)
  return if (isInlinePrompt) inlinePromptManager.getBulbIcon() else null
}