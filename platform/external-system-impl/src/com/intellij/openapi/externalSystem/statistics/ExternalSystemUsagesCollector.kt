// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.statistics

import com.intellij.internal.statistic.beans.UsageDescriptor
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project

class ExternalSystemUsagesCollector : ProjectUsagesCollector() {
  override fun getGroupId() = "statistics.build.tools"

  override fun getUsages(project: Project): Set<UsageDescriptor> {
    val usages = mutableSetOf<UsageDescriptor>()
    for (manager in ExternalSystemApiUtil.getAllManagers()) {
      if (!manager.getSettingsProvider().`fun`(project).getLinkedProjectsSettings().isEmpty()) {
        usages.add(UsageDescriptor(manager.getSystemId().readableName))
      }
    }

    ModuleManager.getInstance(project).modules.find { ExternalSystemModulePropertyManager.getInstance(it).isMavenized() }?.let {
      usages.add(UsageDescriptor("Maven"))
    }
    return usages
  }
}
