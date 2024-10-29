// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

internal object CreateDirectoryUsageCollector: CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("create.directory.dialog", 3)
  private val COMPLETION_VARIANT_CHOSEN = GROUP.registerEvent("completion.variant.chosen", EventFields.Class("contributor"))

  @JvmStatic
  fun logCompletionVariantChosen(project: Project?,
                                 contributorClass: Class<out CreateDirectoryCompletionContributor>) {
    COMPLETION_VARIANT_CHOSEN.log(project, contributorClass)
  }
}