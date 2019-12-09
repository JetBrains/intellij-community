package com.intellij.dvcs.actions

import com.intellij.dvcs.getCommonCurrentBranch
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.actions.IconWithTextAction

@Suppress("unused")
class VcsToolbarAction() : IconWithTextAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val vcsOperationPopupAction =
            ActionManager.getInstance().getAction("Vcs.QuickListPopupAction") ?: error("cannot find VcsOperationPopup action")
        vcsOperationPopupAction.actionPerformed(e)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        updatePresentation(e.presentation, project)

        super.update(e)
    }

    private fun updatePresentation(
        presentation: Presentation,
        project: Project
    ) {
        val vcsPresentationBuild = StringBuilder()
        for (repositoryGroup in VcsRepositoryManager.getInstance(project).repositories.groupBy { it.vcs }) {
            vcsPresentationBuild.append(repositoryGroup.key.name)
            vcsPresentationBuild.append(": ")
            vcsPresentationBuild.append(repositoryGroup.value.getCommonCurrentBranch())
        }

        presentation.text = vcsPresentationBuild.toString()
        presentation.icon = AllIcons.Vcs.Branch

        val component = presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY)

        component?.repaint()
    }
}