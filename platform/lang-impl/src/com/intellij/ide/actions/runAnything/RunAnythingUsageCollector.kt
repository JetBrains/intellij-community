// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything

import com.intellij.internal.statistic.service.fus.collectors.FUSProjectUsageTrigger
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsageTriggerCollector
import com.intellij.openapi.project.Project

private const val GROUP_ID = "statistics.actions.runAnything"

class RunAnythingUsageCollector : ProjectUsageTriggerCollector() {
  override fun getGroupId(): String = GROUP_ID

  companion object {
    fun trigger(project: Project, featureId: String) {
      FUSProjectUsageTrigger.getInstance(project).trigger(RunAnythingUsageCollector::class.java, featureId)
    }
  }
}