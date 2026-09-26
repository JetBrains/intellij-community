// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.backend

import com.intellij.lang.Language
import com.intellij.lang.LanguageUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.application
import org.jetbrains.annotations.ApiStatus

/**
 * Allows invoking a feature provided by a plugin on the non-modal Welcome Screen.
 *
 * This will allow dynamically enabling/disabling the feature based on the plugin's state
 * without breaking IDE customization plugin dependencies.
 *
 * Should either not depend on any other plugin
 * or be registered in a corresponding customization plugin which is not required for the main plugin.
 *
 * This class is a backend part, for UI see `WelcomeScreenFeatureUI`.
 * A feature that needs no backend registers `WelcomeScreenFeatureFrontend` instead.
 */
@ApiStatus.Internal
abstract class WelcomeScreenFeatureBackend {
  companion object {
    private val EP_NAME: ExtensionPointName<WelcomeScreenFeatureBackend> =
      ExtensionPointName.create("com.intellij.platform.ide.welcomeScreenFeatureBackend")

    fun getFeatureIds(): List<String> {
      return EP_NAME.extensionList.map { it.featureKey }
    }

    fun getForFeatureKey(featureKey: String): WelcomeScreenFeatureBackend? {
      return EP_NAME.lazySequence().firstOrNull { it.featureKey == featureKey }
    }

    fun invokeWelcomeScreenAction(project: Project, actionId: String): Boolean {
      return invokeWelcomeScreenAction(project, actionId) { it }
    }

    fun invokeWelcomeScreenAction(project: Project, actionId: String, dataProvider: (DataContext) -> DataContext): Boolean {
      val action = ActionManager.getInstance().getAction(actionId) ?: return false
      application.invokeLater {
        val dataContext = dataProvider(SimpleDataContext.getProjectContext(project))
        val event = AnActionEvent.createEvent(action, dataContext, null, ActionPlaces.WELCOME_SCREEN, ActionUiKind.NONE, null)
        ActionUtil.performAction(action, event)
      }
      return true
    }
  }

  protected abstract val featureKey: String

  abstract fun onClick(project: Project)
}

@ApiStatus.Internal
abstract class WelcomeScreenToolwindowFeatureBackend : WelcomeScreenFeatureBackend() {
  protected abstract val toolWindowId: String

  final override fun onClick(project: Project) {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId)
    toolWindow?.activate(null, true)
  }
}

@ApiStatus.Internal
abstract class WelcomeScreenNewFileFeatureBackend : WelcomeScreenFeatureBackend() {
  protected abstract val languageId: String

  private fun getLanguage(): Language? {
    val language = Language.findLanguageByID(languageId)
    if (language != null) {
      return language
    }

    val fileType = FileTypeRegistry.getInstance().findFileTypeByName(languageId) ?: return null
    return LanguageUtil.getFileTypeLanguage(fileType)
  }

  final override fun onClick(project: Project) {
    val language = getLanguage() ?: return

    invokeWelcomeScreenAction(project, "WelcomeNewEmptyFile") {
      SimpleDataContext.builder().setParent(it).add(CommonDataKeys.LANGUAGE, language).build()
    }
  }
}