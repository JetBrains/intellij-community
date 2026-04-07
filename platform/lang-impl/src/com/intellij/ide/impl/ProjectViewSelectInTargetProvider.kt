@file:ApiStatus.Internal
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.ide.SelectInTarget
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.isProjectViewSplit
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ProjectViewSelectInTargetProvider {
  fun getSelectInTargets(project: Project): Collection<SelectInTarget>
}

internal val ProjectViewSelectInTargetProviderEP: ExtensionPointName<ProjectViewSelectInTargetProvider> =
  ExtensionPointName.create("com.intellij.selectInTargetProvider")

internal fun getProjectViewSelectInTargets(project: Project): Collection<SelectInTarget> {
  return buildList {
    for (provider in ProjectViewSelectInTargetProviderEP.extensionList) {
      try {
        addAll(provider.getSelectInTargets(project))
      }
      catch (e: Throwable) {
        rethrowControlFlowException(e)
        LOG.error("An error has occurred while trying to fetch select in targets from $provider", e)
      }
    }
  }
}

internal class LegacyProjectViewSelectInTargetProvider : ProjectViewSelectInTargetProvider {
  override fun getSelectInTargets(project: Project): Collection<SelectInTarget> {
    if (isProjectViewSplit()) return emptyList() 
    val targets = ProjectView.getInstance(project).getSelectInTargets().sortedBy { it.weight }
    return buildSet {
      val currentId = ProjectView.getInstance(project).getCurrentViewId()
      for (projectViewTarget in targets) {
        if (currentId == projectViewTarget.minorViewId) {
          add(projectViewTarget)
          break
        }
      }
      addAll(targets) // skips the already added one, if any, because it's a set
    }
  }
}

private val LOG = fileLogger()
