// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.actions

import com.intellij.dvcs.ui.RepositoryChangesBrowserNode.Companion.getColorManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.vcs.changes.actions.SetChangesGroupingAction

class SetRepositoryChangesGroupingAction : SetChangesGroupingAction() {
  override val groupingKey: String get() = "repository"

  override fun update(e: AnActionEvent): Unit = super.update(e).also {
    val colorManager = e.project?.let(::getColorManager)

    e.presentation.isEnabledAndVisible = e.presentation.isEnabledAndVisible && colorManager?.isMultipleRoots ?: false
  }
}