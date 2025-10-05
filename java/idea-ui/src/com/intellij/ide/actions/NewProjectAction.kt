// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.JavaUiBundle
import com.intellij.ide.impl.createNewProjectAsync
import com.intellij.ide.projectWizard.NewProjectWizard
import com.intellij.lang.IdeLanguageCustomization
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.wm.impl.welcomeScreen.NewWelcomeScreen
import com.intellij.platform.ProjectGeneratorManager
import com.intellij.ui.ExperimentalUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Service()
private class NewProjectActionCoroutineScopeHolder(@JvmField val coroutineScope: CoroutineScope)

open class NewProjectAction : AnAction(), DumbAware, NewProjectOrModuleAction {
  init {
    getTemplatePresentation().isApplicationScope = true
  }

  companion object {
    internal fun <T> updateActionText(action: T, e: AnActionEvent) where T : AnAction?, T : NewProjectOrModuleAction? {
      val actionText: String?
      if (NewWelcomeScreen.isNewWelcomeScreen(e)) {
        actionText = action!!.templateText
      }
      else {
        val inJavaIde = IdeLanguageCustomization.getInstance().primaryIdeLanguages.contains(JavaLanguage.INSTANCE)
        val fromNewSubMenu = isInvokedFromNewSubMenu(action!!, e)
        actionText = action.getActionText(fromNewSubMenu, inJavaIde)
      }
      e.presentation.setText(actionText)
      action.applyTextOverride(e)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    service<NewProjectActionCoroutineScopeHolder>().coroutineScope.launch {
      runCatching {
        service<ProjectGeneratorManager>().initProjectGenerator(e.project)
      }.onFailure {
        thisLogger().warn("Failed to execute initProjectGenerator", it)
      }
      val wizard = withContext(Dispatchers.EDT) {
        NewProjectWizard(null, ModulesProvider.EMPTY_MODULES_PROVIDER, null)
      }
      createNewProjectAsync(wizard)
    }
  }

  override fun update(e: AnActionEvent) {
    updateActionIcon(e)
    updateActionText(this, e)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun getActionText(isInNewSubmenu: Boolean, isInJavaIde: Boolean): String {
    return JavaUiBundle.message("new.project.action.text", if (isInNewSubmenu) 1 else 0, if (isInJavaIde) 1 else 0)
  }
}

private fun updateActionIcon(e: AnActionEvent) {
  if (NewWelcomeScreen.isNewWelcomeScreen(e)) {
    NewWelcomeScreen.updateNewProjectIconIfWelcomeScreen(e)
  }
  else if (ExperimentalUI.isNewUI() && ActionPlaces.PROJECT_WIDGET_POPUP == e.place) {
    e.presentation.setIcon(AllIcons.General.Add)
  }
}

private fun isInvokedFromNewSubMenu(action: AnAction, e: AnActionEvent): Boolean {
  @Suppress("removal", "DEPRECATION")
  return (e.isFromMainMenu || e.isFromContextMenu) && NewActionGroup.isActionInNewPopupMenu(action)
}
