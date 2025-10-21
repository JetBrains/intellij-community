// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

open class AnActionWrapper(
  private val myDelegate: AnAction
) : AnAction(), ActionWithDelegate<AnAction>, PerformWithDocumentsCommitted {
  init {
    copyFrom(myDelegate)
  }

  override fun getDelegate(): AnAction = myDelegate

  override fun update(e: AnActionEvent) {
    ActionWrapperUtil.update(e, this, myDelegate)
  }

  override fun actionPerformed(e: AnActionEvent) {
    ActionWrapperUtil.actionPerformed(e, this, myDelegate)
  }

  override fun isDumbAware(): Boolean = myDelegate.isDumbAware()

  override fun getActionUpdateThread(): ActionUpdateThread =
    ActionWrapperUtil.getActionUpdateThread(this, myDelegate)

  override fun isPerformWithDocumentsCommitted(): Boolean =
    PerformWithDocumentsCommitted.isPerformWithDocumentsCommitted(myDelegate)

  override fun isInInjectedContext(): Boolean =
    myDelegate.isInInjectedContext
}