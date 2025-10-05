// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.CustomComponentAction

object ActionWrapperUtil {
  @JvmStatic
  fun update(e: AnActionEvent, wrapper: AnAction, delegate: AnAction) {
    val session = e.updateSession
    val customComponent = e.presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY)
    val event = customizeEvent(e, wrapper)
    if (session === UpdateSession.EMPTY || e !== event) delegate.update(event)
    else e.presentation.copyFrom(session.presentation(delegate), customComponent, true)

    if (delegate is CustomComponentAction &&
        (wrapper !is CustomComponentAction) &&
        e.presentation.getClientProperty(ActionUtil.COMPONENT_PROVIDER) == null) {
      e.presentation.putClientProperty(ActionUtil.COMPONENT_PROVIDER, delegate)
    }
  }

  @JvmStatic
  fun getChildren(e: AnActionEvent?, wrapper: ActionGroup, delegate: ActionGroup): Array<AnAction> {
    @Suppress("SSBasedInspection")
    if (e == null) return delegate.getChildren(null)
    val session = e.updateSession
    val event = customizeEvent(e, wrapper)
    return if (session === UpdateSession.EMPTY || e !== event) delegate.getChildren(event)
    else session.children(delegate).toTypedArray()
  }

  @JvmStatic
  fun actionPerformed(e: AnActionEvent, wrapper: AnAction, delegate: AnAction) {
    delegate.actionPerformed(customizeEvent(e, wrapper))
  }

  @JvmStatic
  fun getActionUpdateThread(wrapper: AnAction, delegate: AnAction): ActionUpdateThread {
    if (wrapper is DataSnapshotProvider) return delegate.getActionUpdateThread()
    return ActionUpdateThread.BGT
  }

  @JvmStatic
  private fun customizeEvent(e: AnActionEvent, wrapper: AnAction): AnActionEvent {
    return if (wrapper is DataSnapshotProvider) {
      e.withDataContext(CustomizedDataContext.withSnapshot(e.dataContext, wrapper))
    } else e
  }
}
