package com.intellij.platform.ide.nonModalWelcomeScreen.leftPanel

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.IdeView
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.IdeViewForProjectViewPane
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.util.NlsActions.ActionText
import com.intellij.platform.ide.nonModalWelcomeScreen.NonModalWelcomeScreenBundle
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.DisclosureButton
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.JComponent

internal class WelcomeScreenLeftPanelActions(val project: Project) {
  internal val panelButtonModels: List<PanelButtonModel>
    get() = listOf(
      PanelButtonModel(NonModalWelcomeScreenBundle.message ("welcome.screen.action.open"), AllIcons.Nodes.Folder,
                       runPlatformAction("OpenFile")),
      PanelButtonModel(NonModalWelcomeScreenBundle.message("welcome.screen.action.new"), AllIcons.General.Add,
                       showNewActionGroupDropDown()),
      PanelButtonModel(NonModalWelcomeScreenBundle.message("welcome.screen.action.clone"), AllIcons.General.Vcs,
                       runPlatformAction("Vcs.VcsClone")),
      PanelButtonModel(NonModalWelcomeScreenBundle.message("welcome.screen.action.remote.development"), AllIcons.Nodes.Plugin,
                       runPlatformAction("OpenRemoteDevelopment")),
    )

  internal data class PanelButtonModel(
    @ActionText val text: String,
    val icon: Icon,
    val onClick: (JComponent) -> Unit,
  )

  private fun runPlatformAction(name: String): (JComponent) -> Unit {
    val action = ActionManager.getInstance().getAction(name)
    val presentationFactory = PresentationFactory()

    return { component ->
      val presentation = presentationFactory.getPresentation(action)
      val dataContext = DataManager.getInstance().getDataContext(component)
      val ev = AnActionEvent.createEvent(action, dataContext, presentation, ActionPlaces.WELCOME_SCREEN, ActionUiKind.NONE, null)
      ActionUtil.invokeAction(action, ev, null)
    }
  }

  /**
   * See com.intellij.ide.startup.importSettings.chooser.productChooser.ProductChooserAction.createPopup
   */
  private fun createPopup(step: ListPopupStep<Any>): ListPopup {
    val result = object : ListPopupImpl(null, step) {
      override fun createPopupComponent(content: JComponent?): JComponent {
        return super.createPopupComponent(content).apply {
          preferredSize = Dimension(JBUI.scale(/*UiUtils.DEFAULT_BUTTON_WIDTH*/ 280)
                                      .coerceAtLeast(preferredSize.width), preferredSize.height)
        }
      }
    }
    return result
  }

  private fun showNewActionGroupDropDown(): (JComponent) -> Unit {
    val actionGroup = ActionManager.getInstance().getAction("NonModalWelcomeScreen.LeftTabActions.New") as ActionGroup

    return { component ->
      val dataContext = createDataContext(project, component)
      val step = createStep(actionGroup, dataContext, component)
      createPopup(step).show(
        RelativePoint(component, Point(0, component.height + JBUI.scale(4)))
      )
    }
  }

  private fun createStep(actionGroup: ActionGroup, context: DataContext, widget: JComponent?): ListPopupStep<Any> {
    return JBPopupFactory.getInstance().createActionsStep(actionGroup, context, ActionPlaces.PROJECT_WIDGET_POPUP, false, false,
                                                          null, widget, false, 0, false)
  }

  fun createDataContext(project: Project, component: Component): DataContext {
    return SimpleDataContext.builder()
      .setParent(DataManager.getInstance().getDataContext(component))
      .add<IdeView>(LangDataKeys.IDE_VIEW, getIdeView(project))
      .build()
  }

  /**
   * Needed for new file creation actions as part of the context
   */
  fun getIdeView(project: Project): IdeView {
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

  companion object {
    /**
     * See com.jetbrains.ds.toolwindow.DataSpellDataPanelService.createEmptyStatePanel
     */
    fun Row.leftPanelActionButton(model: PanelButtonModel): Cell<DisclosureButton> {
      return cell(DisclosureButton())
        .applyToComponent {
          this.text = model.text
          this.icon = model.icon
          this.arrowIcon = null

          addActionListener { model.onClick(this) }
        }
    }
  }
}
