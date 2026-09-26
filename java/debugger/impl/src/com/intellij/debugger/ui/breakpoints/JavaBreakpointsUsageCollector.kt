// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.breakpoints

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import kotlinx.coroutines.launch
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties

object JavaBreakpointsUsageCollector : CounterUsagesCollector() {
  private enum class LineBreakpointKind {
    LINE,
    LAMBDA,
    LINE_AND_LAMBDAS,
    RETURN,
  }

  private val GROUP = EventLogGroup("debugger.breakpoints.usage.java", 3)
  private val LINE_BREAKPOINT_KIND_FIELD = EventFields.Enum<LineBreakpointKind>("kind")
  private val LINE_BREAKPOINT_SPECIAL_CASE_FIELD = EventFields.NullableEnum<SpecialLineBreakpointCase>("special_case")
  private val LINE_BREAKPOINT_ADDED = GROUP.registerEvent("line.breakpoint.added",
                                                          EventFields.PluginInfo,
                                                          LINE_BREAKPOINT_KIND_FIELD,
                                                          LINE_BREAKPOINT_SPECIAL_CASE_FIELD)

  @JvmStatic
  fun reportNewBreakpoint(breakpoint: Breakpoint<*>, type: XBreakpointType<*, *>) {
    val properties = breakpoint.properties
    if (type is JavaLineBreakpointType && properties is JavaLineBreakpointProperties) {
      val pluginInfo = getPluginInfo(type.javaClass)
      val lambdaOrdinal = properties.lambdaOrdinal
      val kind = when {
        properties.isConditionalReturn -> LineBreakpointKind.RETURN
        lambdaOrdinal == null -> LineBreakpointKind.LINE_AND_LAMBDAS
        lambdaOrdinal >= 0 -> LineBreakpointKind.LAMBDA
        else -> LineBreakpointKind.LINE
      }

      val project = breakpoint.project
      val xBreakpoint = breakpoint.xBreakpoint as? XLineBreakpoint<*> ?: return
      val fileUrl = xBreakpoint.fileUrl
      val line = xBreakpoint.line
      val coroutineScope = (XDebuggerManager.getInstance(project) as XDebuggerManagerImpl).coroutineScope
      coroutineScope.launch {
        val specialCase = if (kind == LineBreakpointKind.LINE || kind == LineBreakpointKind.LINE_AND_LAMBDAS) {
          getSpecialLineBreakpointCase(project, fileUrl, line)
        }
        else {
          null
        }
        LINE_BREAKPOINT_ADDED.log(project, pluginInfo, kind, specialCase)
      }
    }
  }

  override fun getGroup(): EventLogGroup = GROUP
}
