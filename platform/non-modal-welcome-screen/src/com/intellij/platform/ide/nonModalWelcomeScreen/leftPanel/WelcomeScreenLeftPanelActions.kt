package com.intellij.platform.ide.nonModalWelcomeScreen.leftPanel

import com.intellij.ide.IdeView
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.IdeViewForProjectViewPane
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenActionsUtil
import com.intellij.openapi.wm.impl.welcomeScreen.createToolWindowWelcomeScreenVerticalToolbar
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import java.util.function.Supplier
import javax.swing.JComponent

internal class WelcomeScreenLeftPanelActions(val project: Project) {
  fun createButtonsComponent(): JComponent {
    val actionManager = ActionManager.getInstance()

    // TODO: register group in xml
    val group = DefaultActionGroup()
    actionManager.getAction("WelcomeScreen.OpenDirectoryProject")?.let { group.add(it) }
    actionManager.getAction("NonModalWelcomeScreen.LeftTabActions.New.Action")?.let { group.add(it) }
    actionManager.getAction("ProjectFromVersionControl")?.let { group.add(it) }
    actionManager.getAction("NonModalWelcomeScreen.RemoteDevelopmentActions")?.let { group.add(it) }

    val toolbar = createToolWindowWelcomeScreenVerticalToolbar(group)

    return UiDataProvider.wrapComponent(toolbar.component) { sink ->
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
