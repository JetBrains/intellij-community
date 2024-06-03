// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace.configuration

import com.intellij.icons.ExpUiIcons
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.*
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.ide.wizard.comment.CommentNewProjectWizardStep
import com.intellij.ide.workspace.addToWorkspace
import com.intellij.ide.workspace.isWorkspaceSupportEnabled
import com.intellij.ide.workspace.setWorkspace
import com.intellij.lang.LangBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import org.jetbrains.annotations.Nls
import javax.swing.Icon

class NewWorkspaceWizard: GeneratorNewProjectWizard {
  override val id: String = "jb.workspace"
  override val name: @Nls(capitalization = Nls.Capitalization.Title) String = LangBundle.message("workspace.project.type.name")
  override val icon: Icon = ExpUiIcons.Nodes.Workspace

  override fun createStep(context: WizardContext): NewProjectWizardStep =
    RootNewProjectWizardStep(context)
      .nextStep(::CommentStep)
      .nextStep { parent -> newProjectWizardBaseStepWithoutGap(parent).apply { defaultName = "workspace" } }
      .nextStep(::GitNewProjectWizardStep)
      .nextStep(::Step)

  override fun isEnabled(): Boolean = isWorkspaceSupportEnabled

  private class CommentStep(parent: NewProjectWizardStep) : CommentNewProjectWizardStep(parent) {
    override val comment: @Nls(capitalization = Nls.Capitalization.Sentence) String =
      LangBundle.message("workspace.project.type.comment")
  }

  private class Step(parent: NewProjectWizardStep) : AbstractNewProjectWizardStep(parent) {
    private val subprojectList = SubprojectList(ProjectUtil.getActiveProject())

    override fun setupUI(builder: Panel) {
      builder.row {
        cell(subprojectList.createDecorator().createPanel()).align(Align.FILL)
      }
    }

    override fun setupProject(project: Project) {
      setWorkspace(project)
      StartupManager.getInstance(project).runAfterOpened {
        addToWorkspace(project, subprojectList.projectPaths)
      }
    }
  }
}