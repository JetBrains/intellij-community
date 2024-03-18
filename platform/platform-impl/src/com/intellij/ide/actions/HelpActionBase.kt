// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class HelpActionBase : AnAction() {
  abstract val isAvailable: Boolean
  override fun update(e: AnActionEvent) {
    e.presentation.setEnabledAndVisible(isAvailable)
  }
}
