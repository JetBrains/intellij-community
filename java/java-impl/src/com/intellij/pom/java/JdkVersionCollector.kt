// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.pom.java

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.beans.newMetric
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.util.lang.JavaVersion
import java.util.*

class JdkVersionCollector : ProjectUsagesCollector() {
  override fun getGroupId(): String {
    return "java.jdk.version"
  }

  public override fun getMetrics(project: Project): Set<MetricEvent> {
    val sdks = ModuleManager.getInstance(project).modules.mapNotNullTo(HashSet()) {
      ModuleRootManager.getInstance(it).sdk
    }

    val jdkVersions = sdks.mapTo(HashSet()) {
      JavaVersion.tryParse(it.versionString)
    }

    return jdkVersions.mapTo(HashSet()) {
      newMetric("MODULE_JDK_VERSION", FeatureUsageData().apply {
        addData("feature", it?.feature ?: -1)
        addData("minor", it?.minor ?: -1)
        addData("update", it?.update ?: -1)
        addData("ea", it?.ea ?: false)
      })
    }
  }

  override fun requiresReadAccess() = true
}
