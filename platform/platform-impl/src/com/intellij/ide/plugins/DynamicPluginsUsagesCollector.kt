// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import org.jetbrains.annotations.ApiStatus.Internal

private val GROUP = EventLogGroup(
  id = "plugins.dynamic",
  version = 3,
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
internal class DynamicPluginsUsagesCollector : CounterUsagesCollector() {

  override fun getGroup() = GROUP
}