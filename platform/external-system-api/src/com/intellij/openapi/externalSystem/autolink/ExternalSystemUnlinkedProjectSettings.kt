// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autolink

import com.intellij.openapi.project.Project

interface ExternalSystemUnlinkedProjectSettings {
  var isEnabledAutoLink: Boolean

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ExternalSystemUnlinkedProjectSettings {
      return project.getService(ExternalSystemUnlinkedProjectSettings::class.java)
    }
  }
}