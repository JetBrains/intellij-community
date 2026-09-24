// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions

import com.intellij.ide.TrustedFiles
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.annotations.ApiStatus

/**
 * Gates the run actions on the trust of the context file (see [TrustedFiles]).
 *
 * A run configuration created from a file executes the content of that file. The run gutter icon
 * is hidden for a file opened in the safe mode, but the context action, its shortcut, the editor
 * context menu, and the "Current File" item of the run widget reached the same configuration (IJPL-255678).
 * Every entry point that creates a configuration from a context asks this object first.
 */
@ApiStatus.Internal
object ExecutionSafeMode {
  /** Returns `true` when a run configuration may be created for [file] in [project]. */
  @JvmStatic
  fun isRunAllowed(project: Project, file: VirtualFile?): Boolean {
    return file == null || TrustedFiles.isTrusted(file, project)
  }

  /** Returns `true` when a run configuration may be created from [context]. */
  @JvmStatic
  fun isRunAllowed(context: ConfigurationContext): Boolean {
    val location = context.location
    val project = location?.project ?: context.dataContext.getData(CommonDataKeys.PROJECT) ?: return true
    val file = location?.virtualFile ?: context.dataContext.getData(CommonDataKeys.VIRTUAL_FILE)
    return isRunAllowed(project, file)
  }

  /**
   * Returns `true` when a run configuration may be created from [element].
   *
   * The file is taken with [PsiUtilCore.getVirtualFile], so a non-physical element backed by a real
   * file, a decompiled `.class` for example, is judged by that file. An element backed by no file at
   * all is allowed.
   */
  @JvmStatic
  fun isRunAllowed(element: PsiElement?): Boolean {
    if (element == null) return true
    return isRunAllowed(element.project, PsiUtilCore.getVirtualFile(element))
  }
}
