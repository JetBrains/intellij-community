// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.leftPanel

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import javax.swing.JComponent

/**
 * See [com.intellij.ide.startup.importSettings.chooser.productChooser.ProductChooserAction]
 */
internal class WelcomeScreenLeftTabActionNew : DumbAwareAction() {
  companion object {
    /**
     * See [com.intellij.ide.startup.importSettings.chooser.ui.UiUtils.DEFAULT_BUTTON_WIDTH]
     */
    private const val DEFAULT_BUTTON_WIDTH = 280
  }

  override fun actionPerformed(e: AnActionEvent) {
    // From com.intellij.platform.ide.nonModalWelcomeScreen.leftPanel.LeftPanelDisclosureButtonAction
    val component = e.inputEvent?.component as? JComponent
                           ?: e.presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY)

    val actionGroup = ActionManager.getInstance().getAction("NonModalWelcomeScreen.LeftTabActions.New") as ActionGroup
    val step = createStep(actionGroup, e.dataContext, component)
    val popup = createPopup(step)

    if (component != null) {
      val targetPoint = RelativePoint(component, Point(0, component.height + JBUI.scale(4)))
      popup.show(targetPoint)
    }
    else {
      popup.showInBestPositionFor(e.dataContext)
    }
  }

  private fun createStep(actionGroup: ActionGroup, context: DataContext, widget: Component?): ListPopupStep<Any> {
    return JBPopupFactory.getInstance().createActionsStep(actionGroup, context, ActionPlaces.PROJECT_WIDGET_POPUP, false, false,
                                                          null, widget, false, 0, false)
  }

  private fun createPopup(step: ListPopupStep<Any>): ListPopup {
    val result = object : ListPopupImpl(null, step) {
      override fun createPopupComponent(content: JComponent?): JComponent {
        return super.createPopupComponent(content).apply {
          preferredSize = Dimension(JBUI.scale(DEFAULT_BUTTON_WIDTH)
                                      .coerceAtLeast(preferredSize.width), preferredSize.height)
        }
      }
    }
    return result
  }
}
