// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId2
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.psi.stubs.StubInconsistencyReporter.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object StubInconsistencyReportUtil {
  val GROUP = EventLogGroup("mismatch.in.stub.indexes", 1, "FUS",
                            "Collector for breakages of indexes defined in implementation-level terms, " +
                            "see more at https://youtrack.jetbrains.com/articles/IJPL-A-308")

  private val KOTLIN_DESCRIPTOR_EVENT = GROUP.registerEvent("kotlin.descriptor.not.found")

  @JvmStatic
  fun reportKotlinDescriptorNotFound(project: Project?) {
    KOTLIN_DESCRIPTOR_EVENT.log(project)
  }

  private val FOUND_IN_KOTLIN_FULL_CLASS_NAME_INDEX_FIELD = EventFields.Boolean("found_in_KotlinFullClassNameIndex")
  private val FOUND_IN_EVERYTHING_SCOPE_FIELD = EventFields.Boolean("found_in_everything_scope")
  private val KOTLIN_MISSING_CLASS_NAME_EVENT: EventId2<Boolean, Boolean> = GROUP.registerEvent(
    "found.missing.class.name.in.Kotlin",
    FOUND_IN_KOTLIN_FULL_CLASS_NAME_INDEX_FIELD,
    FOUND_IN_EVERYTHING_SCOPE_FIELD
  )

  @JvmStatic
  fun reportKotlinMissingClassName(
    project: Project,
    foundInKotlinFullClassNameIndex: Boolean,
    foundInEverythingScope: Boolean
  ) {
    KOTLIN_MISSING_CLASS_NAME_EVENT.log(project, foundInKotlinFullClassNameIndex, foundInEverythingScope)
  }

  private val STUB_TREE_AND_INDEX_DO_NOT_MATCH_SOURCE_FIELD = EventFields.Enum<StubTreeAndIndexDoNotMatchSource>("source")
  private val STUB_TREE_AND_INDEX_DO_NOT_MATCH_EVENT = GROUP.registerVarargEvent(
    "found.not.matching.stub.tree.from.psi.and.index",
    STUB_TREE_AND_INDEX_DO_NOT_MATCH_SOURCE_FIELD
  )

  @JvmStatic
  fun reportStubTreeAndIndexDoNotMatch(project: Project, source: StubTreeAndIndexDoNotMatchSource) {
    STUB_TREE_AND_INDEX_DO_NOT_MATCH_EVENT.log(project, EventPair(STUB_TREE_AND_INDEX_DO_NOT_MATCH_SOURCE_FIELD, source))
  }

  private val CHECK_REASON_FIELD = EventFields.Enum<SourceOfCheck>("reason")
  private val INCONSISTENCY_TYPE_FIELD = EventFields.Enum<InconsistencyType>("type")
  private val STUB_INCONSISTENCY_EVENT = GROUP.registerVarargEvent(
    "found.stub.tree.from.text.not.matching.one.from.psi", CHECK_REASON_FIELD, INCONSISTENCY_TYPE_FIELD
  )

  @JvmStatic
  fun reportStubInconsistencyBetweenPsiAndText(project: Project, reason: SourceOfCheck?, type: InconsistencyType) {
    val parameters = mutableListOf<EventPair<*>>(INCONSISTENCY_TYPE_FIELD.with(type))
    reason?.let { parameters.add(CHECK_REASON_FIELD.with(it)) }
    STUB_INCONSISTENCY_EVENT.log(project, parameters)
  }
}


class StubInconsistencyFusCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = StubInconsistencyReportUtil.GROUP
}