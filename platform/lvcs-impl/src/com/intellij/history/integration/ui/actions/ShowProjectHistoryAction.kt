// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.integration.ui.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.guessProjectDir

internal class ShowProjectHistoryAction : ShowHistoryAction() {
  override fun getFile(e: AnActionEvent) = e.project?.guessProjectDir()
}