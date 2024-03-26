// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId2
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object StubInconsistencyReportUtil {
  val GROUP = EventLogGroup("stub.inconsistency", 1)

  private val KOTLIN_DESCRIPTOR_EVENT = GROUP.registerEvent("kotlin.descriptor.not.found")

  @JvmStatic
  fun reportKotlinDescriptorNotFound(project: Project?) {
    KOTLIN_DESCRIPTOR_EVENT.log(project)
  }

  private val FOUND_IN_KOTLIN_FULL_CLASS_NAME_INDEX_FIELD = EventFields.Boolean("foundInKotlinFullClassNameIndex")
  private val FOUND_IN_EVERYTHING_SCOPE_FIELD = EventFields.Boolean("foundInEverythingScope")
  private val KOTLIN_MISSING_CLASS_NAME_EVENT: EventId2<Boolean, Boolean> = GROUP.registerEvent(
    "kotlin.missing.class.name",
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
}


class StubInconsistencyFusCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = StubInconsistencyReportUtil.GROUP
}