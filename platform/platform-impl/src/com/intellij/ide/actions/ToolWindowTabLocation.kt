// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.internal.statistic.eventLog.events.EnumEventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.ui.ComponentUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import java.awt.Component

/**
 * Indicates whether tool window content is displayed in a tool window
 * or in an editor tab.
 */
@ApiStatus.Internal
enum class ToolWindowTabLocation {
  TOOL_WINDOW,
  EDITOR,
}

@ApiStatus.Internal
object ToolWindowTabLocationFusUtil {
  @JvmField
  val TOOLWINDOW_TAB_LOCATION: EnumEventField<ToolWindowTabLocation> = EventFields.Enum(
    "toolwindow_tab_location",
    ToolWindowTabLocation::class.java,
    "Location of the tool window content: tool window or editor tab",
  )

  @JvmStatic
  @RequiresEdt
  fun fromComponentHierarchy(component: Component?): ToolWindowTabLocation? {
    return when {
      component == null -> null
      ComponentUtil.getParentOfType(InternalDecoratorImpl::class.java, component) != null -> ToolWindowTabLocation.TOOL_WINDOW
      ComponentUtil.getParentOfType(EditorsSplitters::class.java, component) != null -> ToolWindowTabLocation.EDITOR
      else -> null
    }
  }
}
