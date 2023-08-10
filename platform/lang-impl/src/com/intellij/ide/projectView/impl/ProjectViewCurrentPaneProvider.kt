// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface ProjectViewCurrentPaneProvider {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): ProjectViewCurrentPaneProvider? = project.serviceOrNull<ProjectViewCurrentPaneProvider>()
  }

  fun getCurrentPaneId(): String?
}