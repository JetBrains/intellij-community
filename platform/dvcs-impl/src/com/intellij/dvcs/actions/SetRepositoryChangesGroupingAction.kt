// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.actions

import com.intellij.dvcs.ui.RepositoryChangesBrowserNode.Companion.getColorManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.vcs.changes.actions.SetChangesGroupingAction
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport.Companion.REPOSITORY_GROUPING

private class SetRepositoryChangesGroupingAction : SetChangesGroupingAction() {
  override val groupingKey: String get() = REPOSITORY_GROUPING

  override fun update(e: AnActionEvent): Unit = super.update(e).also {
    val colorManager = e.project?.let(::getColorManager)

    e.presentation.isEnabledAndVisible = e.presentation.isEnabledAndVisible && colorManager?.hasMultiplePaths() ?: false
  }
}
