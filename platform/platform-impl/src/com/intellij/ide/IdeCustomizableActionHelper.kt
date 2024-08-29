// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.ui.IdeUICustomization
import java.util.concurrent.atomic.AtomicBoolean

class IdeCustomizableActionHelper(private val action: AnAction) {
  private val id by lazy { ActionManager.getInstance().getId(action)!! }
  private val templatePresentationInitialized = AtomicBoolean()

  fun update(e: AnActionEvent) {
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
      initializePresentation(action.templatePresentation)
    }

    initializePresentation(e.presentation)
  }
}