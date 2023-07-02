// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization

import com.intellij.ide.ProjectWindowCustomizerService
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.ColorChooserService
import com.intellij.ui.awt.RelativePoint
import java.awt.Point

/**
 * @author Konstantin Bulenkov
 */
private class ChangeMainToolbarColorAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val ideFrame = IdeFocusManager.getInstance(project).lastFocusedFrame
    var relativePoint: RelativePoint? = null
    if (ideFrame != null) {
      relativePoint = RelativePoint(ideFrame.component, Point(200, 30))
    }

    ColorChooserService.instance.showPopup(project = project,
                                           currentColor = ProjectWindowCustomizerService.getInstance().getToolbarBackground(project),
                                           listener = { color, _ ->
                                             ProjectWindowCustomizerService.getInstance().setToolbarColor(color, project)
                                           },
                                           location = relativePoint)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.project != null &&
                               UISettings.getInstance().differentiateProjects &&
                               ProjectWindowCustomizerService.getInstance().getPaintingType().isGradient()
  }
}
