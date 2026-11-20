// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.wizard.impl.showGeneratorNewProjectWizard
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.welcomeScreen.NewWelcomeScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.nio.file.Path

private const val DEFAULT_NEW_PROJECT_ACTION_ID = "WelcomeScreen.DefaultNewProjectAction"

private fun getRealAction(): AnAction? {
  return ActionManager.getInstance().getAction(NewProjectEntryPointImpl.WELCOME_SCREEN_NEW_PROJECT_ACTION_ID)
}

/**
 * Shows the new project wizard of the platform, see [com.intellij.ide.wizard.impl.GeneratorNewProjectWizardDialog].
 * It shows one wizard at a time.
 */
@Service
internal class DefaultNewProjectWizardService(private val coroutineScope: CoroutineScope) {
  @Volatile
  private var job: Job? = null

  fun isWizardInProgress(): Boolean {
    return job?.isActive == true
  }

  fun showWizard(project: Project?, location: Path?, attachTo: Project?) {
    if (isWizardInProgress()) {
      thisLogger().warn("Previous execution hasn't finished yet")
      return
    }
    job = coroutineScope.launch {
      showGeneratorNewProjectWizard(project, location, attachTo)
    }
  }
}

/**
 * The New Project item of the welcome screen. It is the action of IDEA, or [DefaultNewProjectAction] in a product without it.
 */
internal class DefaultNewProjectActionGroup : DefaultActionGroup(), DumbAware {
  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val action = getRealAction() ?: ActionManager.getInstance().getAction(DEFAULT_NEW_PROJECT_ACTION_ID) ?: return EMPTY_ARRAY
    return arrayOf(action)
  }
}

/**
 * Shows the new project wizard of the platform in a product that has no new project action of IDEA.
 */
internal class DefaultNewProjectAction : AnAction(), DumbAware {
  init {
    templatePresentation.isApplicationScope = true
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = getRealAction() == null
    e.presentation.isEnabled = e.presentation.isEnabled && !service<DefaultNewProjectWizardService>().isWizardInProgress()
    NewWelcomeScreen.updateNewProjectIconIfWelcomeScreen(e)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun actionPerformed(e: AnActionEvent) {
    service<DefaultNewProjectWizardService>().showWizard(
      project = e.project,
      location = e.getData(NewProjectEntryPoint.PROJECT_LOCATION),
      attachTo = e.getData(NewProjectEntryPoint.ATTACH_TO_PROJECT),
    )
  }
}
