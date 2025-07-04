// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.impl.content.ContentTabLabel
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.ui.ClientProperty
import com.intellij.ui.content.Content

internal fun AnActionEvent.guessCurrentContent(): Content? {
  val component = getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
  val manager = getData(ToolWindowContentUi.CONTENT_MANAGER_DATA_KEY)
  return if (component is ContentTabLabel) {
    component.content
  }
  else manager?.getSelectedContent()
}

internal fun AnActionEvent.findNearestDecorator(): InternalDecoratorImpl? {
  val component = getData(PlatformDataKeys.CONTEXT_COMPONENT)
  return InternalDecoratorImpl.findNearestDecorator(component)
}