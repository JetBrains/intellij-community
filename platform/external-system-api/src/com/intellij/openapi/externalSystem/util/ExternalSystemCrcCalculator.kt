// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.util

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface ExternalSystemCrcCalculator {
  fun isApplicable(systemId: ProjectSystemId, file: VirtualFile): Boolean

  fun calculateCrc(project: Project, file: VirtualFile, fileText: CharSequence): Long?

  companion object {
    val EP_NAME = ExtensionPointName.create<ExternalSystemCrcCalculator>("com.intellij.externalSystemCrcCalculator")

    @JvmStatic
    fun getInstance(systemId: ProjectSystemId, file: VirtualFile): ExternalSystemCrcCalculator? {
      return EP_NAME.findFirstSafe { it.isApplicable(systemId, file) }
    }
  }
}