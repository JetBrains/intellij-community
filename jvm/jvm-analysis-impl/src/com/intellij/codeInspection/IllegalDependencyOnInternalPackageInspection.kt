// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil
import com.intellij.java.codeserver.core.JavaPsiModuleUtil
import com.intellij.packageDependencies.DependenciesBuilder
import com.intellij.pom.java.JavaFeature
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.light.LightJavaModule
import com.intellij.psi.util.PsiUtil

class IllegalDependencyOnInternalPackageInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = IllegalDependencyOnInternalPackage(holder)
}

private class IllegalDependencyOnInternalPackage(private val holder: ProblemsHolder) : PsiElementVisitor() {
  override fun visitFile(psiFile: PsiFile) {
    if (!PsiUtil.isAvailable(JavaFeature.MODULES, psiFile) || JavaModuleGraphUtil.findDescriptorByElement(psiFile) != null) return
    DependenciesBuilder.analyzeFileDependencies(psiFile) { place, dependency ->
      if (dependency !is PsiClass) return@analyzeFileDependencies
      val dependencyFile = dependency.containingFile
      if (dependencyFile !is PsiClassOwner || !dependencyFile.isPhysical || dependencyFile.virtualFile == null) return@analyzeFileDependencies
      val javaModule = JavaModuleGraphUtil.findDescriptorByElement(dependencyFile)
      if (javaModule == null || javaModule is LightJavaModule) return@analyzeFileDependencies
      val moduleName = javaModule.name
      if (moduleName.startsWith("java.")) return@analyzeFileDependencies
      val packageName = dependencyFile.packageName
      if (JavaPsiModuleUtil.exports(javaModule, packageName, null)) return@analyzeFileDependencies
      holder.registerProblem(
        place,
        JvmAnalysisBundle.message("inspection.message.illegal.dependency.module.doesn.t.export", moduleName, packageName)
      )
    }
  }
}