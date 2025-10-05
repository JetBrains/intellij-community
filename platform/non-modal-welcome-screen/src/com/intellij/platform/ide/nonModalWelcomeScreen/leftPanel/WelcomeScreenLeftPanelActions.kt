package com.intellij.platform.ide.nonModalWelcomeScreen.leftPanel

import com.intellij.ide.IdeView
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.IdeViewForProjectViewPane
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenActionsUtil
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.ui.components.DisclosureButton
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.event.ActionEvent
import java.util.function.Supplier
import javax.swing.AbstractAction
import javax.swing.JComponent

internal class WelcomeScreenLeftPanelActions(val project: Project) {
  fun createButtonsComponent(): JComponent {
    val actionManager = ActionManager.getInstance()

    // TODO: register group in xml
    val group = DefaultActionGroup()
    actionManager.getAction("WelcomeScreen.OpenDirectoryProject")?.let { group.add(it) }
    actionManager.getAction("NonModalWelcomeScreen.LeftTabActions.New.Action")?.let { group.add(it) }
    actionManager.getAction("ProjectFromVersionControl")?.let { group.add(it) }
    actionManager.getAction("OpenRemoteDevelopment")?.let { group.add(it) }

    val toolbar = ActionToolbarImpl(ActionPlaces.WELCOME_SCREEN, LeftPanelActionGroupWrapper(group), false, false, false)
    toolbar.isOpaque = false
    toolbar.isReservePlaceAutoPopupIcon = false
    toolbar.layoutStrategy = VerticalToolbarLayoutStrategy()

    toolbar.targetComponent = toolbar

    // + JBInsets(3) from DarculaDisclosureButtonBorder
    toolbar.border = JBUI.Borders.empty(/* top = */ 5, /* left = */ 17, /* bottom = */ 0, /* right = */ 17)

    return UiDataProvider.wrapComponent(toolbar) { sink ->
      sink[WelcomeScreenActionsUtil.NON_MODAL_WELCOME_SCREEN] = true
      sink[CommonDataKeys.PROJECT] = project
      sink[LangDataKeys.IDE_VIEW] = getIdeView(project)
    }
  }

  /**
   * Needed for new file creation actions as part of the context
   */
  private fun getIdeView(project: Project): IdeView {
    val projectViewPane = ProjectView.getInstance(project).getCurrentProjectViewPane()
    val baseView = IdeViewForProjectViewPane(Supplier { projectViewPane })
    return object : IdeView {
      override fun getDirectories(): Array<PsiDirectory> {
        val elements = orChooseDirectory ?: return PsiDirectory.EMPTY_ARRAY
        return arrayOf(elements)
      }

      override fun getOrChooseDirectory(): PsiDirectory? {
        val projectDir = project.guessProjectDir() ?: return null
        return PsiManager.getInstance(project).findDirectory(projectDir)
      }

      override fun selectElement(element: PsiElement) {
        baseView.selectElement(element)
      }
    }
  }
}

private class LeftPanelDisclosureButtonAction(private val actionDelegate: AnAction) : CustomComponentAction {
  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val button = DisclosureButton()
    button.arrowIcon = null

    button.text = presentation.text
    button.icon = presentation.icon

    button.action = object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        performAction(button, presentation)
      }
    }

    return button
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    if (component !is DisclosureButton) return

    component.text = presentation.text
    component.icon = presentation.icon ?: EmptyIcon.ICON_16
    UIUtil.setEnabled(component, presentation.isEnabled, true)
  }

  private fun performAction(component: JComponent, presentation: Presentation) {
    val dataContext = ActionToolbar.getDataContextFor(component)
    val ev = AnActionEvent.createEvent(actionDelegate, dataContext, presentation, ActionPlaces.WELCOME_SCREEN, ActionUiKind.NONE, null)
    ActionUtil.invokeAction(actionDelegate, ev, null)
  }
}

private class LeftPanelActionGroupWrapper(group: ActionGroup) : ActionGroupWrapper(group) {
  override fun postProcessVisibleChildren(e: AnActionEvent, visibleChildren: List<AnAction>): List<AnAction> {
    visibleChildren.forEach { action ->
      e.updateSession.presentation(action).putClientProperty(ActionUtil.COMPONENT_PROVIDER,
                                                             LeftPanelDisclosureButtonAction(action))
    }
    return visibleChildren
  }
}

private class VerticalToolbarLayoutStrategy : ToolbarLayoutStrategy {
  private val unscaledVerticalGap = 2 // + 3 * 2 = 8, from DarculaDisclosureButtonBorder

  override fun calculateBounds(toolbar: ActionToolbar): List<Rectangle> {
    val res = mutableListOf<Rectangle>()

    val bounds = toolbar.component.bounds
    val insets = toolbar.component.insets

    var yOffset = 0
    for (child in toolbar.component.components) {
      val d = if (child.isVisible) child.preferredSize else Dimension()
      res.add(Rectangle(insets.left, insets.top + yOffset, bounds.width - insets.left - insets.right, d.height))
      yOffset += d.height + JBUI.scale(unscaledVerticalGap)
    }

    return res;
  }

  override fun calcPreferredSize(toolbar: ActionToolbar): Dimension {
    var width = 0
    var height = 0

    for (component in toolbar.component.components.filter { it.isVisible }) {
      val preferredSize = component.preferredSize
      width = maxOf(width, preferredSize.width)
      height += preferredSize.height + JBUI.scale(unscaledVerticalGap)
    }
    if (height > 0) height -= JBUI.scale(unscaledVerticalGap)
    val result = JBUI.size(width, height)
    JBInsets.addTo(result, toolbar.component.insets)
    return result
  }

  override fun calcMinimumSize(toolbar: ActionToolbar): Dimension = calcPreferredSize(toolbar)
}

