// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.ui.treeStructure.ProjectViewUpdateCause

@Service(Level.PROJECT)
internal class ProjectViewPerformanceMonitor {
  companion object {
    @JvmStatic fun getInstance(project: Project): ProjectViewPerformanceMonitor = project.service()
  }

  fun reportUpdateAll(causes: Collection<ProjectViewUpdateCause>) {
    LOG.debug { "The entire PV is updated because $causes" }
  }
}

private val LOG = logger<ProjectViewPerformanceMonitor>()
