// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAware

class StepOverInstructionAction : SessionActionBase(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    debuggerSession(e)?.stepOverInstruction() ?: thisLogger().error("Inconsistent update")
  }
}
