// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.actionSystem.AnAction

interface SearchFieldActionsContributor {
  /**
   * Creates actions that are placed on the right side of SE search input field
   */
  fun createRightActions(onChanged: Runnable): List<AnAction>
}