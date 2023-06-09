// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet.impl.pointers

import com.intellij.facet.pointers.FacetPointersManager
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

internal class FacetPointersPostStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) : Unit = blockingContext {
    val manager = FacetPointersManager.getInstance(project)
    if (manager is FacetPointersManagerImpl) {
      DumbService.getInstance(project).smartInvokeLater {
        manager.refreshPointers()
      }
    }
  }
}