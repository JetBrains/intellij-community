// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.logs

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.jetbrains.mlapi.feature.Feature
import com.jetbrains.mlapi.feature.FeatureDeclaration
import com.jetbrains.mlapi.logs.LogsEventRegister
import com.jetbrains.mlapi.logs.MLEventLogger
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
class IntelliJFusEventRegister(private val baseEventGroup: EventLogGroup) : LogsEventRegister {
  private class Logger(
    private val varargEventId: VarargEventId,
    private val objectDescription: ConverterObjectDescription
  ) : MLEventLogger {
    override fun log(features: List<Feature>) {
      val ijEventPairs = objectDescription.buildEventPairs(features)
      varargEventId.log(*ijEventPairs.toTypedArray())
    }
  }

  override fun registerEvent(name: String, declarations: List<FeatureDeclaration<*>>): MLEventLogger {
    val objectDescription = ConverterObjectDescription(declarations)
    val varargEventId = baseEventGroup.registerVarargEvent(name,  *objectDescription.getFields())
    return Logger(varargEventId, objectDescription)
  }
}
