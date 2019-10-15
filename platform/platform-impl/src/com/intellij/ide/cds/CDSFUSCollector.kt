// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.cds

import com.intellij.internal.statistic.beans.newBooleanMetric
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector

class CDSApplicationUsagesCollector : ApplicationUsagesCollector() {
  override fun getGroupId() = "intellij.cds"

  override fun getMetrics() = setOf(
    newBooleanMetric("running.with.cds", CDSManager.isRunningWithCDS)
  )
}
