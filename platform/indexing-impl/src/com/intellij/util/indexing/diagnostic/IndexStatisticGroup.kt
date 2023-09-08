// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.openapi.project.Project
import com.intellij.util.indexing.ID
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
object IndexStatisticGroup {
  val GROUP = EventLogGroup("indexing.statistics", 9)

  private val stubIndexInconsistencyRegistered = GROUP.registerEvent("stub.index.inconsistency")

  @JvmStatic
  fun reportStubIndexInconsistencyRegistered(project: Project) {
    stubIndexInconsistencyRegistered.log(project)
  }

  private val indexIdField =
    EventFields.StringValidatedByCustomRule("index_id", IndexIdRuleValidator::class.java)
  private val rebuildCauseField =
    EventFields.Class("rebuild_cause")
  private val insideIndexInitialization =
    EventFields.Boolean("inside_index_initialization")

  private val indexRebuildEvent = GROUP.registerVarargEvent (
    "index_rebuild",
    indexIdField,
    rebuildCauseField,
    insideIndexInitialization,
  )

  @JvmStatic
  fun reportIndexRebuild(indexId: ID<*, *>,
                         cause: Throwable,
                         isInsideIndexInitialization: Boolean) {
    indexRebuildEvent.log(indexIdField with indexId.name,
                          rebuildCauseField with cause.javaClass,
                          insideIndexInitialization with isInsideIndexInitialization)
  }
}