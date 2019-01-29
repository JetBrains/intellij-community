// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything

import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.openapi.project.Project

private const val GROUP_ID =  "actions.runAnything"

class RunAnythingUsageCollector {
  companion object {
    fun trigger(project: Project, featureId: String) {
      FUCounterUsageLogger.getInstance().logEvent(project, GROUP_ID, featureId)
    }
  }
}