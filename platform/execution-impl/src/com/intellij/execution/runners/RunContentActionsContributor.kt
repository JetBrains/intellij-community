// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runners

import com.intellij.openapi.actionSystem.AnAction

/**
 * This interface allows an [com.intellij.execution.ui.ExecutionConsole] to add actions to the run tool window toolbar.
 */
interface RunContentActionsContributor {
  /**
   * @return Actions to be added to the toolbar.
   */
  fun getActions(): Array<AnAction>

  /**
   * @return Actions to be added to the more menu.
   */
  fun getAdditionalActions(): Array<AnAction> = AnAction.EMPTY_ARRAY

  fun hideOriginalActions(): Unit = Unit
}