// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.breakpoints

import com.intellij.lang.LanguageExtension
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus

/**
 * A special case of a line breakpoint position where a breakpoint may hit
 * unexpectedly many times or never hit at all.
 */
@ApiStatus.Internal
enum class SpecialLineBreakpointCase {
  /** The line starts with the `for`/`while` keyword of a loop statement. */
  LOOP_START,

  /** The line starts with the closing brace of a loop body. */
  LOOP_END,

  /** The line starts with the `try`, `catch` or `finally` keyword. */
  TRY_CATCH_FINALLY_START,

  /** The line starts with the closing brace of a `try`, `catch` or `finally` block. */
  TRY_CATCH_FINALLY_END,

  /** The line starts with the closing brace of the then-branch of an `if` statement. */
  IF_THEN_END,

  /** The line starts with the closing brace of the else-branch of an `if` statement. */
  IF_ELSE_END,

  /** The line starts with the closing brace of a `switch` branch. */
  SWITCH_CASE_END,
}

@ApiStatus.Internal
interface JvmSpecialLineBreakpointCaseProvider {
  /**
   * Classifies the given [line] whether it falls to a "special case"
   * when the breakpoint may (not) hit, counterintuitively to a user.
   * 
   * Return `null` if the line does not fall to a "special case" in the file's language.
   */
  @RequiresReadLock
  fun getSpecialCase(project: Project, file: PsiFile, line: Int): SpecialLineBreakpointCase?

  companion object {
    internal val EP_NAME = LanguageExtension<JvmSpecialLineBreakpointCaseProvider>("com.intellij.debugger.specialLineBreakpointCaseProvider")
  }
}

internal suspend fun getSpecialLineBreakpointCase(project: Project, fileUrl: String, line: Int): SpecialLineBreakpointCase? =
  readAction {
    val virtualFile = VirtualFileManager.getInstance().findFileByUrl(fileUrl) ?: return@readAction null
    val file = PsiManager.getInstance(project).findFile(virtualFile) ?: return@readAction null
    JvmSpecialLineBreakpointCaseProvider.EP_NAME.forLanguage(file.language)?.getSpecialCase(project, file, line)
  }
