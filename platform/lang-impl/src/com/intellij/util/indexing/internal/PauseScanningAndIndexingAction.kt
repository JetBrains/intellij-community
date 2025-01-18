// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.internal

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service

private class PauseScanningAndIndexingAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    e.project?.service<InternalIndexingActionsService>()?.pauseScanningAndIndexingAndRunEmptyTask()
  }
}
