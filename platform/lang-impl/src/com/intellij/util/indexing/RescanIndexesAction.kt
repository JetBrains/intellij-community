// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing

import com.intellij.ide.actions.cache.RecoveryAction
import com.intellij.lang.LangBundle
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls

internal class RescanIndexesAction : RecoveryAction {
  override val performanceRate: Int
    get() = 9990
  override val presentableName: @Nls(capitalization = Nls.Capitalization.Title) String
    get() = LangBundle.message("rescan.indexes.recovery.action.name")
  override val actionKey: String
    get() = "rescan"

  override fun perform(project: Project?) {
    project!!
    DumbService.getInstance(project).queueTask(UnindexedFilesUpdater(project))
  }

  override fun canBeApplied(project: Project?): Boolean = project != null
}