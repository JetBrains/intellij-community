// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lang.lsWidget.impl.fus

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId2
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Records actions related to the `Language Services` status bar widget ([com.intellij.platform.lang.lsWidget.impl.LanguageServiceWidget]).
 * Most of the actions returned by the [com.intellij.platform.lang.lsWidget.LanguageServiceWidgetItem.createWidgetMainAction]
 * and [com.intellij.platform.lang.lsWidget.LanguageServiceWidgetItem.createWidgetInlineActions] functions
 * use this collector to track the widget item usages. They use it either directly
 * or via a service like [com.intellij.platform.lang.lsWidget.internal.LanguageServiceWidgetActionsService]
 * or [com.intellij.platform.lsp.api.lsWidget.LspWidgetInternalService].
 */
@ApiStatus.Internal
object LanguageServiceWidgetUsagesCollector : CounterUsagesCollector() {
  private val GROUP: EventLogGroup = EventLogGroup("language.services.widget", 1)

  private val ACTION_INVOKED: EventId2<LanguageServiceWidgetActionKind, Class<*>> =
    GROUP.registerEvent("action.invoked",
                        EventFields.Enum("action_kind", LanguageServiceWidgetActionKind::class.java),
                        EventFields.Class("language_service_class"))

  override fun getGroup(): EventLogGroup = GROUP

  fun actionInvoked(project: Project, actionKind: LanguageServiceWidgetActionKind, serviceSpecificClass: Class<*>) {
    ACTION_INVOKED.log(project, actionKind, serviceSpecificClass)
  }
}


@ApiStatus.Internal
enum class LanguageServiceWidgetActionKind {
  OpenSettings,
  RestartService,
  StopService,
}
