// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.pom.NavigatableWithText
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
class ProjectViewEditSourceAction : BaseNavigateToSourceAction(true) {
  override fun update(e: AnActionEvent) {
    super.update(e)
    if (!e.presentation.isVisible || !e.presentation.isEnabled) return
    val navigatables = getNavigatables(e.dataContext)
    e.presentation.isEnabledAndVisible =
      navigatables != null &&
      navigatables
        .filterIsInstance<NavigatableWithText>()
        .mapNotNull { it.getNavigateActionText(true) }
        .isNotEmpty()
  }
}
