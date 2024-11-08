// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUsersOnboarding

import com.intellij.ide.actions.SettingsEntryPointAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.UiComponentsUtil
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStep
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingStepData
import com.intellij.platform.ide.newUiOnboarding.NewUiOnboardingUtil
import com.intellij.ui.GotItComponentBuilder
import com.intellij.ui.components.JBList
import com.intellij.ui.popup.PopupFactoryImpl.ActionItem
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.yield
import java.awt.Point

internal class SettingsPopupStep : NewUiOnboardingStep {
  override suspend fun performStep(project: Project, disposable: CheckedDisposable): NewUiOnboardingStepData? {
    val settingsButton = UiComponentsUtil.findUiComponent(project) { button: ActionButton ->
      button.action is SettingsEntryPointAction
    } ?: return null

    val action = settingsButton.action as SettingsEntryPointAction

    val popup = NewUiOnboardingUtil.showNonClosablePopup(
      disposable,
      createPopup = { NewUiOnboardingUtil.createPopupFromActionButton(settingsButton) { event -> action.createPopup(event) } },
      showPopup = { popup -> popup.showUnderneathOf(settingsButton) }
    ) ?: return null

    yield()  // Wait for the popup to be shown

    val actionsList = UIUtil.findComponentOfType(popup.content, JBList::class.java) ?: return null
    val actionManager = serviceAsync<ActionManager>()
    val pluginsActionIndex = (0 until actionsList.model.size).find { index ->
      val element = actionsList.model.getElementAt(index) as? ActionItem ?: return@find false
      actionManager.getId(element.action) == "WelcomeScreen.Plugins"
    } ?: return null

    val pluginsActionBounds = actionsList.getCellBounds(pluginsActionIndex, pluginsActionIndex)

    val builder = GotItComponentBuilder(NewUsersOnboardingBundle.message("settings.popup.step.text"))
    builder.withHeader(NewUsersOnboardingBundle.message("settings.popup.step.header"))

    val point = Point(JBUI.scale(-4), pluginsActionBounds.y + pluginsActionBounds.height / 2)
    val relativePoint = NewUiOnboardingUtil.convertPointToFrame(project, actionsList, point) ?: return null
    return NewUiOnboardingStepData(builder, relativePoint, Balloon.Position.atLeft)
  }
}