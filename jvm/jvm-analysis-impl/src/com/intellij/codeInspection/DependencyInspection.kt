// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.options.JavaInspectionButtons
import com.intellij.codeInsight.options.JavaInspectionControls
import com.intellij.codeInspection.options.OptPane
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.packageDependencies.DependenciesBuilder
import com.intellij.packageDependencies.DependencyRule
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.packageDependencies.ui.DependencyConfigurable
import com.intellij.psi.PsiFile
import com.intellij.util.SmartList
import com.intellij.util.containers.FactoryMap

class DependencyInspection : AbstractBaseUastLocalInspectionTool() {
  override fun getOptionsPane(): OptPane = OptPane.pane(
    JavaInspectionControls.button(JavaInspectionButtons.ButtonKind.DEPENDENCY_CONFIGURATION),
  )

  override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    val validationManager = DependencyValidationManager.getInstance(file.project)
    if (!validationManager.hasRules() || validationManager.getApplicableRules(file).isEmpty()) return null
    val problems: MutableList<ProblemDescriptor> = SmartList()
    val violations = FactoryMap.create<PsiFile, Array<DependencyRule>> { dependencyFile ->
      validationManager.getViolatorDependencyRules(file, dependencyFile!!)
    }
    DependenciesBuilder.analyzeFileDependencies(file) { place, dependency ->
      dependency.containingFile?.let { dependencyFile ->
        violations[dependencyFile]?.forEach { dependencyRule ->
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

    override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
      return IntentionPreviewInfo.Html(JvmAnalysisBundle.message("jvm.inspections.dependency.intention.description"))
    }
  }
}