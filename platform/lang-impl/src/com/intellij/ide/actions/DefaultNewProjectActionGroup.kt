// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.util.projectWizard.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.platform.DirectoryProjectGenerator

private class DefaultNewProjectStep : AbstractNewProjectStep<Any>(DefaultNewProjectStepCustomization()) {
  private class DefaultNewProjectStepCustomization : Customization<Any>() {
    override fun createEmptyProjectGenerator(): DirectoryProjectGenerator<Any> {
      return EmptyWebProjectTemplate()
    }

    override fun createProjectSpecificSettingsStep(
      projectGenerator: DirectoryProjectGenerator<Any>,
      callback: AbstractCallback<Any>,
    ): ProjectSettingsStepBase<Any> {
      return ProjectSettingsStepBase(projectGenerator, callback);
    }

    override fun getActions(generators: List<DirectoryProjectGenerator<*>>, callback: AbstractCallback<Any>): Array<out AnAction> {
      return generators
        .partition { it is WebProjectTemplate }
        .let { listOf("Web" to it.first, "Other" to it.second) }
        .map { DefaultActionGroup(it.first, super.getActions(it.second, callback).toList()) }
        .toTypedArray()
    }
  }
}

private fun getRealAction(): AnAction? {
  return ActionManager.getInstance().getAction("WelcomeScreen.CreateNewProject")
}

private class DefaultNewProjectActionGroup : DefaultActionGroup(), DumbAware {
  private fun getActualAction(): AnAction {
    return getRealAction() ?: DefaultNewProjectStep()
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    return arrayOf(getActualAction())
  }
}

private class DefaultNewProjectAction : AnAction(), DumbAware {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = getRealAction() == null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun actionPerformed(e: AnActionEvent) {
    object : AbstractNewProjectDialog() {
      override fun createNewProjectStep(): AbstractNewProjectStep<*> {
        return DefaultNewProjectStep()
      }
    }.show()
  }
}