// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.breakpoints

import com.intellij.lang.LanguageExtension
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class TrickyLineBreakpointLocation {
  LOOP_START,
  LOOP_END,
  TRY_CATCH_FINALLY,
  TRY_CATCH_FINALLY_END,
  IF_THEN_END,
  IF_ELSE_END,
  SWITCH_CASE_END,
}

@ApiStatus.Internal
interface JvmTrickyLineBreakpointLocationProvider {
  @RequiresReadLock
  fun getLineBreakpointLocation(project: Project, file: PsiFile, line: Int): TrickyLineBreakpointLocation?

  companion object {
    internal val EP_NAME = LanguageExtension<JvmTrickyLineBreakpointLocationProvider>("com.intellij.debugger.trickyLineBreakpointLocationProvider")
  }
}

@RequiresReadLock
internal fun getLineBreakpointLocation(project: Project, fileUrl: String, line: Int): TrickyLineBreakpointLocation? {
  val virtualFile = VirtualFileManager.getInstance().findFileByUrl(fileUrl) ?: return null
  val file = PsiManager.getInstance(project).findFile(virtualFile) ?: return null
  return JvmTrickyLineBreakpointLocationProvider.EP_NAME.forLanguage(file.language)?.getLineBreakpointLocation(project, file, line)
}
