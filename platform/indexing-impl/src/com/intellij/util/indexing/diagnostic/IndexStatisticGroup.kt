// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.openapi.project.Project

object IndexStatisticGroup {
  val GROUP = EventLogGroup("indexing.statistics", 8)

  private val stubIndexInconsistencyRegistered = GROUP.registerEvent("stub.index.inconsistency")

  internal fun reportStubIndexInconsistencyRegistered(project: Project) {
    stubIndexInconsistencyRegistered.log(project)
  }
}