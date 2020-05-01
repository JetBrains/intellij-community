// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project

interface ExternalSystemProjectTrackerSettings {

  var autoReloadType: AutoReloadType

  enum class AutoReloadType {
    /**
     * Auto-reload project after any changes to build files
     */
    ALL,

    /**
     * Auto-reload project after VCS updates and changes to build files made outside IDE
     */
    SELECTIVE,

    /**
     * Don't auto-reload project
     */
    NONE
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ExternalSystemProjectTrackerSettings {
      return ServiceManager.getService(project, ExternalSystemProjectTrackerSettings::class.java)
    }
  }
}