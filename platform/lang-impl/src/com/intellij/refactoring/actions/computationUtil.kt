// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.readAction

internal fun <T> computeWithProgressIconUnderReadAction(e: AnActionEvent, action: () -> T): T {
  return Utils.computeWithProgressIcon(e.dataContext, e.place) {
    readAction(action)
  }
}