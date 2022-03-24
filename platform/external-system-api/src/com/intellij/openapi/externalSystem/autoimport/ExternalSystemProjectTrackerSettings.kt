// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.NonExtendable
interface ExternalSystemProjectTrackerSettings {

  var autoReloadType: AutoReloadType

  enum class AutoReloadType {
    /**
     * Reloads a project after any changes made to build script files
     */
    ALL,

    /**
     * Reloads a project after VCS updates and changes made to build script files outside the IDE
     */
    SELECTIVE,

    /**
     * Reloads a project only if cached data is corrupted, invalid or missing
     */
    NONE
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ExternalSystemProjectTrackerSettings {
      return project.getService(ExternalSystemProjectTrackerSettings::class.java)
    }
  }
}