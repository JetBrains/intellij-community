// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal object TrustedProjectsStatistics : CounterUsagesCollector() {

  val GROUP: EventLogGroup = EventLogGroup("trusted_projects", 3)
  val NEW_PROJECT_OPEN_OR_IMPORT_CHOICE: EventId1<OpenUntrustedProjectChoice> = GROUP.registerEvent("open_new_project",
                                                                                                    EventFields.Enum("choice",
                                                                                                                     OpenUntrustedProjectChoice::class.java))
  val LOAD_UNTRUSTED_PROJECT_CONFIRMATION_CHOICE: EventId1<Boolean> = GROUP.registerEvent("load_untrusted_project_confirmation",
                                                                                          EventFields.Boolean("agree-to-load"))
  val PROJECT_IMPLICITLY_TRUSTED_BY_PATH: EventId = GROUP.registerEvent("project_implicitly_trusted_by_path")
  val PROJECT_IMPLICITLY_TRUSTED_BY_URL: EventId = GROUP.registerEvent("project_implicitly_trusted_by_url")
  val TRUST_HOST_CHECKBOX_SELECTED: EventId = GROUP.registerEvent("trust_host_checkbox_selected")
  val TRUST_LOCATION_CHECKBOX_SELECTED: EventId = GROUP.registerEvent("trust_location_checkbox_selected")
  val TRUST_PROJECT_FROM_BANNER: EventId = GROUP.registerEvent("trust_project_from_notification_banner")
  val READ_MORE_FROM_BANNER: EventId = GROUP.registerEvent("read_more_from_notification_banner")

  override fun getGroup(): EventLogGroup = GROUP
}