// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.plugin.freeze

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.DumbAwareAction

internal class ResetFreezeNotificationStateAction: DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    WriteAction.run<RuntimeException> {
      PluginsFreezesService.getInstance().reset()
    }
  }
}