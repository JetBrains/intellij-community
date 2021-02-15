// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.packageDependencies.DependenciesBuilder
import com.intellij.packageDependencies.DependencyRule
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.packageDependencies.ui.DependencyConfigurable
import com.intellij.psi.PsiFile
import com.intellij.util.SmartList
import com.intellij.util.containers.FactoryMap
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class DependencyInspection : AbstractBaseUastLocalInspectionTool() {
  override fun createOptionsPanel(): JComponent = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
    add(JButton(JvmAnalysisBundle.message("jvm.inspections.dependency.configure.button.text")).apply {
      addActionListener {
        val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(this))
                      ?: ProjectManager.getInstance().defaultProject
        ShowSettingsUtil.getInstance().editConfigurable(this, DependencyConfigurable(project))
      }
    })
  }

  override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    val validationManager = DependencyValidationManager.getInstance(file.project)
    if (!validationManager.hasRules() || validationManager.getApplicableRules(file).isEmpty()) return null
    val problems: MutableList<ProblemDescriptor> = SmartList()
    val violations = FactoryMap.create<PsiFile, Array<DependencyRule>> { dependencyFile ->
      validationManager.getViolatorDependencyRules(file, dependencyFile!!)
    }
    DependenciesBuilder.analyzeFileDependencies(file) { place, dependency ->
      val dependencyFile = dependency.containingFile
      if (dependencyFile != null && dependencyFile.isPhysical && dependencyFile.virtualFile != null) {
        for (dependencyRule in violations[dependencyFile]!!) {
          val message = JvmAnalysisBundle.message("jvm.inspections.dependency.violator.problem.descriptor", dependencyRule.displayText)
          val fixes = arrayOf(EditDependencyRulesAction(dependencyRule))
          problems.add(manager.createProblemDescriptor(place, message, isOnTheFly, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING))
        }
      }
    }

    return if (problems.isEmpty()) null else problems.toTypedArray()
  }

  private class EditDependencyRulesAction(private val myRule: DependencyRule) : LocalQuickFix {
    override fun getName(): String = JvmAnalysisBundle.message("jvm.inspections.dependency.edit.rules.text", myRule.displayText)

    override fun getFamilyName(): String = JvmAnalysisBundle.message("jvm.inspections.dependency.edit.rules.family")

    override fun startInWriteAction() = false

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      ShowSettingsUtil.getInstance().editConfigurable(project, DependencyConfigurable(project))
    }
  }
}