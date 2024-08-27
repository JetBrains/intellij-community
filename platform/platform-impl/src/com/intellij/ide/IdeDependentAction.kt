// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.IdeUICustomization
import java.util.concurrent.atomic.AtomicBoolean

abstract class IdeDependentAction : DumbAwareAction() {
  private val id by lazy { ActionManager.getInstance().getId(this)!! }
  private val templatePresentationInitialized = AtomicBoolean()

  override fun update(e: AnActionEvent) {
    super.update(e)

    fun initializePresentation(p: Presentation) {
      val customization = IdeUICustomization.getInstance()
      customization.getActionText(id)?.let {
        p.text = it
      }
      customization.getActionDescription(id)?.let {
        p.description = it
      }
    }

    if (!templatePresentationInitialized.getAndSet(true)) { // one-time init
      initializePresentation(templatePresentation)
    }

    initializePresentation(e.presentation)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isDumbAware(): Boolean = true
}