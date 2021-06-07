// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface ExternalSystemSettingsFileCrcCalculator {
  fun isApplicable(id: ProjectSystemId, file: VirtualFile): Boolean

  fun calculateCrc(project: Project, file: VirtualFile, fileText: CharSequence): Long?

  companion object {
    val EP_NAME = ExtensionPointName.create<ExternalSystemSettingsFileCrcCalculator>(
      "com.intellij.externalSystemSettingsFileCrcCalculator"
    )

    @JvmStatic
    fun getInstance(systemId: ProjectSystemId, file: VirtualFile): ExternalSystemSettingsFileCrcCalculator? {
      return EP_NAME.findFirstSafe { it.isApplicable(systemId, file) }
    }
  }
}