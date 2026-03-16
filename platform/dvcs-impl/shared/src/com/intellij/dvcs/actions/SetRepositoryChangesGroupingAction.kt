// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.vcs.changes.actions.SetChangesGroupingAction
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport.Companion.REPOSITORY_GROUPING
import com.intellij.platform.vcs.impl.shared.VcsMappingsHolder

internal class SetRepositoryChangesGroupingAction : SetChangesGroupingAction() {
  override val groupingKey: String get() = REPOSITORY_GROUPING

  override fun update(e: AnActionEvent): Unit = super.update(e).also {
    val mappingsHolder = e.project?.let { VcsMappingsHolder.getInstance(it) }
    e.presentation.isEnabledAndVisible = e.presentation.isEnabledAndVisible &&
                                         mappingsHolder != null &&
                                         mappingsHolder.hasMultipleRoots()
  }
}
