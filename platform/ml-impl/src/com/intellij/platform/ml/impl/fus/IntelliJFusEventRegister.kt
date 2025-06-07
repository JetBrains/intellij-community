// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.fus

import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.jetbrains.ml.tools.logs.FusEventLogger
import com.jetbrains.ml.tools.logs.FusEventRegister
import org.jetbrains.annotations.ApiStatus
import com.intellij.internal.statistic.eventLog.EventLogGroup as IJEventLogGroup
import com.jetbrains.ml.api.logs.EventField as MLEventField
import com.jetbrains.ml.api.logs.EventPair as MLEventPair
import com.jetbrains.ml.api.logs.ObjectDescription as MLObjectDescription


@ApiStatus.Internal
class IntelliJFusEventRegister(private val baseEventGroup: IJEventLogGroup) : FusEventRegister {
  private class Logger(
    private val varargEventId: VarargEventId,
    private val objectDescription: ConverterObjectDescription
  ) : FusEventLogger {
    override fun log(eventPairs: List<MLEventPair<*>>) {
      val ijEventPairs = objectDescription.buildEventPairs(eventPairs)
      varargEventId.log(*ijEventPairs.toTypedArray())
    }
  }

  override fun registerEvent(name: String, eventFields: List<MLEventField<*>>): FusEventLogger {
    val objectDescription = ConverterObjectDescription(MLObjectDescription(eventFields))
    val varargEventId = baseEventGroup.registerVarargEvent(name, null, *objectDescription.getFields())
    return Logger(varargEventId, objectDescription)
  }
}
