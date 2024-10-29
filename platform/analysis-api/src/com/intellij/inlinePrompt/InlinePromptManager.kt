// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("InlinePrompt")

package com.intellij.inlinePrompt

import com.intellij.openapi.components.service
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
  /**
   * Checks if the inline prompt is currently shown in the specified editor.
   *
   * @param editor the editor in which the inline prompt visibility is to be checked
   * @return true if the inline prompt is shown, false otherwise
   */
  fun isInlinePromptShown(editor: Editor, line: Int?): Boolean

  /**
   * Checks if inline prompt code is currently being generated in the specified editor.
   *
   * @param editor the editor in which the code generation status of the inline prompt is to be checked
   * @return true if inline prompt code is being generated, false otherwise
   */
  fun isInlinePromptCodeGenerating(editor: Editor, line: Int?): Boolean

  fun getBulbIcon(): Icon?
}

/**
 * Checks if the inline prompt is currently shown in the specified editor.
 *
 * @param editor the editor in which the inline prompt visibility is to be checked
 * @param line the line which is inspected for having inline prompt. If `null`, then the function all lines are checked
 * @param project the project associated with the editor, defaults to the project of the editor if not provided
 * @return true if the inline prompt is shown, false otherwise
 */
@Experimental
@JvmOverloads
fun isInlinePromptShown(editor: Editor, line: Int? = null, project: Project? = editor.project): Boolean {
  if (project == null) return false
  return project.service<InlinePromptManager>().isInlinePromptShown(editor, line)
}

/**
 * Checks if the inline prompt generation UI elements are active.
 *
 * @param editor the editor in which the inline prompt visibility is to be checked
 * @param line the line which is inspected for having active generation UI. If `null`, then the function all lines are checked
 * @param project the project associated with the editor, defaults to the project of the editor if not provided
 * @return true if the inline prompt is shown, false otherwise
 */
@Experimental
@JvmOverloads
fun isInlinePromptGenerating(editor: Editor, line: Int? = null, project: Project? = editor.project): Boolean {
  if (project == null) return false
  return project.service<InlinePromptManager>().isInlinePromptCodeGenerating(editor, line)
}

/**
 * @return the inline prompt bulb icon if an inline prompt is currently shown or is being generated in the specified editor.
 */
@Internal
fun getInlinePromptBulbIcon(project: Project, editor: Editor): Icon? {
  val inlinePromptManager = project.service<InlinePromptManager>()
  val isInlinePrompt = inlinePromptManager.isInlinePromptShown(editor, null) || inlinePromptManager.isInlinePromptCodeGenerating(editor, null)
  return if (isInlinePrompt) inlinePromptManager.getBulbIcon() else null
}