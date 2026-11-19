// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.console

import com.intellij.execution.actions.ClearConsoleAction
import com.intellij.execution.actions.ConsoleActionsPostProcessor
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.util.Key
import com.intellij.ui.ClientProperty
import javax.swing.JComponent

internal val BUILD_CONSOLE_V2: Key<Boolean> = Key.create("BuildConsoleViewImplV2")

@Suppress("SplitModeApiUsage")
internal class BuildConsoleActionsPostProcessor : ConsoleActionsPostProcessor() {

  override fun postProcessPopupActions(console: ConsoleView, actions: Array<AnAction>): Array<AnAction> {
    if (console !is JComponent || !ClientProperty.isTrue(console, BUILD_CONSOLE_V2)) {
      return actions
    }
    return actions
      .filterNot { it is ClearConsoleAction }
      .toTypedArray()
  }
}
