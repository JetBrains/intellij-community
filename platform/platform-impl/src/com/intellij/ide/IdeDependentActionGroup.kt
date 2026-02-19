// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.IdeUICustomization

open class IdeDependentActionGroup : DefaultActionGroup() {
  private val id by lazy { ActionManager.getInstance().getId(this)!! }

  override fun update(e: AnActionEvent) {
    super.update(e)
    IdeUICustomization.getInstance().getActionText(id)?.let {
      e.presentation.text = it
    }
    IdeUICustomization.getInstance().getActionDescription(id)?.let {
      e.presentation.description = it
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isDumbAware(): Boolean = true
}
