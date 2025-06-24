// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.BooleanEventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.LongEventField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.time.Duration

@Internal
internal object DynamicPluginsUsagesCollector : CounterUsagesCollector() {

  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup(
    id = "plugins.dynamic",
    version = 4,
  )

  private val LOAD_SUCCESS_EVENT = GROUP.registerEvent(
    eventId = "load.success",
    eventField1 = EventFields.PluginInfoByDescriptor,
  )

  private val UNLOAD_SUCCESS_EVENT = GROUP.registerEvent(
    eventId = "unload.success",
    eventField1 = EventFields.PluginInfoByDescriptor,
  )

  private val UNLOAD_FAIL_EVENT = GROUP.registerEvent(
    eventId = "unload.failure",
    eventField1 = EventFields.PluginInfoByDescriptor,
  )

  private val LOAD_PAID_PLUGINS_EVENT = GROUP.registerEvent(
    eventId = "load.paid",
    eventField1 = LongEventField(name = "duration_s", description = "Paid plugins loading duration in whole seconds"),
    eventField2 = EventFields.Count,
    eventField3 = BooleanEventField("restart_required"),
  )

  @Internal
  internal fun logDescriptorLoad(
    descriptor: IdeaPluginDescriptor,
  ) {
    LOAD_SUCCESS_EVENT.log(descriptor)
  }

  @Internal
  internal fun logDescriptorUnload(
    descriptor: IdeaPluginDescriptor,
    success: Boolean,
  ) {
    (if (success) UNLOAD_SUCCESS_EVENT else UNLOAD_FAIL_EVENT)
      .log(descriptor)
  }

  @Internal
  internal fun logPaidPluginsLoaded(
    duration: Duration,
    count: Int,
    restartRequired: Boolean,
  ) {
    LOAD_PAID_PLUGINS_EVENT.log(duration.inWholeSeconds, count, restartRequired)
  }
}
