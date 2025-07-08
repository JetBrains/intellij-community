// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

open class ActionGroupWrapper(
  private val myDelegate: ActionGroup
) : ActionGroup(), ActionWithDelegate<ActionGroup>, PerformWithDocumentsCommitted {
  init {
    copyFrom(myDelegate)
  }

  override fun getDelegate(): ActionGroup = myDelegate

  override fun getChildren(e: AnActionEvent?): Array<out AnAction> {
    return ActionWrapperUtil.getChildren(e, this, myDelegate)
  }

  override fun update(e: AnActionEvent) {
    ActionWrapperUtil.update(e, this, myDelegate)
  }

  override fun actionPerformed(e: AnActionEvent) {
    ActionWrapperUtil.actionPerformed(e, this, myDelegate)
  }

  override fun postProcessVisibleChildren(e: AnActionEvent, visibleChildren: List<AnAction>): List<AnAction> {
    return myDelegate.postProcessVisibleChildren(e, visibleChildren)
  }

  override fun isDumbAware(): Boolean = myDelegate.isDumbAware()

  override fun getActionUpdateThread(): ActionUpdateThread =
    ActionWrapperUtil.getActionUpdateThread(this, myDelegate)

  override fun isPerformWithDocumentsCommitted(): Boolean =
    PerformWithDocumentsCommitted.isPerformWithDocumentsCommitted(myDelegate)

  override fun isInInjectedContext(): Boolean =
    myDelegate.isInInjectedContext
}