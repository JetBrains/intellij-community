// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions

import com.intellij.execution.configurations.RunConfigurationWithTargetFile
import com.intellij.execution.configurations.RunProfile
import com.intellij.ide.TrustedFiles
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.annotations.ApiStatus

/**
 * Prevents running files that are opened in safe mode.
 *
 * Run configurations created from editor or context actions are checked before creation.
 * Configurations created manually are checked at launch time if they expose their target file via
 * [RunConfigurationWithTargetFile].
 *
 * Non-physical PSI elements are resolved to their original file when possible. Elements or run
 * profiles without a backing file are allowed.
 */
@ApiStatus.Internal
object ExecutionSafeMode {
  /** Returns whether [file] may be used to create or run a configuration in [project]. */
  @JvmStatic
  fun isRunAllowed(project: Project, file: VirtualFile?): Boolean {
    return file == null || TrustedFiles.isTrusted(file, project)
  }

  /** Returns whether a run configuration may be created from [context]. */
  @JvmStatic
  fun isRunAllowed(context: ConfigurationContext): Boolean {
    val location = context.location
    val project = location?.project ?: context.dataContext.getData(CommonDataKeys.PROJECT) ?: return true
    val file = location?.virtualFile ?: context.dataContext.getData(CommonDataKeys.VIRTUAL_FILE)
    return isRunAllowed(project, file)
  }

  /**
   * Returns whether a run configuration may be created from [element].
   *
   * For non-physical PSI, the original backing file is used when available.
   */
  @JvmStatic
  fun isRunAllowed(element: PsiElement?): Boolean {
    if (element == null) return true
    return isRunAllowed(element.project, PsiUtilCore.getVirtualFile(element))
  }

  /**
   * Returns the untrusted file targeted by [profile], or `null` if execution may proceed.
   *
   * Profiles that do not implement [RunConfigurationWithTargetFile] are not checked here.
   */
  @JvmStatic
  fun findUntrustedTargetFile(project: Project, profile: RunProfile?): VirtualFile? {
    val targetFile = (profile as? RunConfigurationWithTargetFile)?.getTargetFile() ?: return null
    return targetFile.takeIf { !TrustedFiles.isTrusted(it, project) }
  }
}
