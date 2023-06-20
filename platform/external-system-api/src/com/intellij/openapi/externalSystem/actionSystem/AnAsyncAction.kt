// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.actionSystem

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

abstract class AnAsyncAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val cs = CoroutineScope(SupervisorJob())
    cs.launch { actionPerformedAsync(e) }
  }

  abstract suspend fun actionPerformedAsync(e: AnActionEvent)
}