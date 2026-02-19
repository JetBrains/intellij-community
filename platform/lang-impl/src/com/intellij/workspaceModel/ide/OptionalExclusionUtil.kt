// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.Experimental
object OptionalExclusionUtil {
  private val EP_NAME: ExtensionPointName<OptionalExclusionContributor> =
    ExtensionPointName("com.intellij.workspaceModel.optionalExclusionContributor")

  @JvmStatic
  fun exclude(project: Project, fileOrDir: VirtualFile): Boolean {
    for (exclusionContributor in EP_NAME.extensionList) {
      if (exclusionContributor.requestExclusion(project, fileOrDir)) {
        return true
      }
    }
    return false
  }

  @JvmStatic
  fun canCancelExclusion(project: Project, fileOrDir: VirtualFile): Boolean {
    return EP_NAME.extensionList.any {
      it.canCancelExclusion(project, fileOrDir)
    }
  }

  @JvmStatic
  fun cancelExclusion(project: Project, fileOrDir: VirtualFile): Boolean {
    var cancelled = false
    for (exclusionContributor in EP_NAME.extensionList) {
      if (exclusionContributor.requestExclusionCancellation(project, fileOrDir)) {
        cancelled = true
      }
    }
    return cancelled
  }
}
