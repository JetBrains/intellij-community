// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.actionSystem.AnAction
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Deprecated("The old Search Everywhere API is being sunset. " +
            "Use com.intellij.platform.searchEverywhere.frontend.SeFilterEditor#getSearchFieldActions instead.")
interface SearchFieldActionsContributor {
  /**
   * Creates actions that are placed on the right side of SE search input field
   */
  fun createRightActions(registerShortcut: (AnAction) -> Unit, onChanged: Runnable): List<AnAction>
}