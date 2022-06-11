// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil
import com.intellij.packageDependencies.DependenciesBuilder
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.light.LightJavaModule
import com.intellij.psi.util.PsiUtil
import com.intellij.util.SmartList

class IllegalDependencyOnInternalPackageInspection : AbstractBaseUastLocalInspectionTool() {
  override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    if (!PsiUtil.isLanguageLevel9OrHigher(file) || 
        JavaModuleGraphUtil.findDescriptorByElement(file) != null) {
      return null
    }
    val problems: MutableList<ProblemDescriptor> = SmartList()
    DependenciesBuilder.analyzeFileDependencies(file) { place, dependency ->
      if (dependency is PsiClass) {
        val dependencyFile = dependency.containingFile
        if (dependencyFile is PsiClassOwner && dependencyFile.isPhysical && dependencyFile.virtualFile != null) {
          val javaModule = JavaModuleGraphUtil.findDescriptorByElement(dependencyFile)
          if (javaModule == null || javaModule is LightJavaModule) {
            return@analyzeFileDependencies
          }

          val moduleName = javaModule.name
          if (moduleName.startsWith("java.")) {
            return@analyzeFileDependencies
          }
          val packageName = dependencyFile.packageName
          if (!JavaModuleGraphUtil.exports(javaModule, packageName, null)) {
            problems.add(manager.createProblemDescriptor(place,
                                                         JvmAnalysisBundle.message("inspection.message.illegal.dependency.module.doesn.t.export", moduleName, packageName), null as LocalQuickFix?, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly))
          }
        }
      }
    }

    return if (problems.isEmpty()) null else problems.toTypedArray()
  }
}