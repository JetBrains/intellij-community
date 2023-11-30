// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.util

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

/**
 * Calculates CRC for build scripts of applicable build systems.
 * Used to provide custom CRC calculation rules for scripts.
 */
@ApiStatus.Experimental
interface ExternalSystemCrcCalculator {
  /**
   * Checks that CRC calculation is applicable to defined build system [systemId] and build script [file].
   */
  fun isApplicable(systemId: ProjectSystemId, file: VirtualFile): Boolean

  /**
   * Calculates CRC for [fileText]. [file] content may be not equal to [fileText],
   * for example [fileText] can be taken from corresponding [com.intellij.openapi.editor.Document].
   */
  fun calculateCrc(project: Project, file: VirtualFile, fileText: CharSequence): Long?

  companion object {
    val EP_NAME: ExtensionPointName<ExternalSystemCrcCalculator> = ExtensionPointName.create<ExternalSystemCrcCalculator>("com.intellij.externalSystemCrcCalculator")

    @JvmStatic
    fun getInstance(systemId: ProjectSystemId, file: VirtualFile): ExternalSystemCrcCalculator? {
      return EP_NAME.findFirstSafe { it.isApplicable(systemId, file) }
    }
  }
}