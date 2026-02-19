// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class IdeDependentAction : DumbAwareAction() {
  private val customizableActionHelper by lazy { IdeCustomizableActionHelper(this) }

  override fun update(e: AnActionEvent) {
    super.update(e)
    customizableActionHelper.update(e)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isDumbAware(): Boolean = true
}