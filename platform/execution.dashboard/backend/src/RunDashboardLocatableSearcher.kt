// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.backend

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.execution.dashboard.RunDashboardManagerImpl
import com.intellij.platform.execution.serviceView.backend.ServiceViewLocatableSearcher
import com.intellij.psi.util.PsiUtilCore

internal class RunDashboardLocatableSearcher : ServiceViewLocatableSearcher {
  override fun find(project: Project, virtualFile: VirtualFile): List<String> {
    val result = mutableListOf<String>()

    val services = RunDashboardManagerImpl.getInstance(project).runConfigurations
    for (service in services) {
      val customizers = RunDashboardManagerImpl.getCustomizers(service.configurationSettings, service.descriptor)
      for (customizer in customizers) {
        val psiElement = customizer.getPsiElement(service.configurationSettings.configuration) ?: continue
        if (virtualFile == PsiUtilCore.getVirtualFile(psiElement)) {
          result.add(service.serviceViewId)
          break
        }
      }
    }

    return result;
  }
}