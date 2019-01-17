// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything

import com.intellij.internal.statistic.eventLog.FeatureUsageGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger
import com.intellij.internal.statistic.utils.createData
import com.intellij.openapi.project.Project

private val GROUP_ID =  FeatureUsageGroup("statistics.actions.runAnything",1)

class RunAnythingUsageCollector {
  companion object {
    fun trigger(project: Project, featureId: String) {
      FeatureUsageLogger.log(GROUP_ID, featureId, createData(project, null))
    }
  }
}