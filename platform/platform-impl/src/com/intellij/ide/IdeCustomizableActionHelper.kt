// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.IdeUICustomization

class IdeCustomizableActionHelper(private val action: AnAction) {
  private val id by lazy { ActionManager.getInstance().getId(action)!! }

  fun update(e: AnActionEvent) {
    IdeUICustomization.getInstance().getActionText(id)?.let {
      e.presentation.text = it
    }
    IdeUICustomization.getInstance().getActionDescription(id)?.let {
      e.presentation.description = it
    }
  }
}