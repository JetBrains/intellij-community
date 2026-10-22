// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface CombinedDiffManager {
  fun createProcessor(
    diffPlace: String? = null,
    contextActions: List<AnAction>? = null,
    goToChangeToolbarActions: List<AnAction>? = null,
  ): CombinedDiffComponentProcessor

  companion object {
    fun getInstance(project: Project): CombinedDiffManager = project.service()
  }
}

internal class CombinedDiffManagerImpl(private val project: Project) : CombinedDiffManager {
  override fun createProcessor(
    diffPlace: String?,
    contextActions: List<AnAction>?,
    goToChangeToolbarActions: List<AnAction>?,
  ): CombinedDiffComponentProcessor {
    val model = CombinedDiffModel(project)
    model.context.putUserData(DiffUserDataKeys.PLACE, diffPlace)
    model.context.putUserData(DiffUserDataKeys.CONTEXT_ACTIONS, contextActions)
    return CombinedDiffComponentProcessorImpl(model, null)
  }
}
