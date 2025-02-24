// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl

import com.intellij.codeInsight.JavaModuleSystemEx
import com.intellij.codeInsight.JavaModuleSystemEx.ErrorWithFixes
import com.intellij.codeInsight.daemon.JavaErrorBundle
import com.intellij.codeInsight.daemon.impl.quickfix.AddExportsDirectiveFix
import com.intellij.codeInsight.daemon.impl.quickfix.AddExportsOptionFix
import com.intellij.codeInsight.daemon.impl.quickfix.AddModulesOptionFix
import com.intellij.codeInsight.daemon.impl.quickfix.AddRequiresDirectiveFix
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.QuickFixFactory
import com.intellij.java.JavaBundle
import com.intellij.java.codeserver.core.JpmsModuleAccessInfo
import com.intellij.java.codeserver.core.JpmsModuleAccessInfo.JpmsModuleAccessMode
import com.intellij.java.codeserver.core.JpmsModuleAccessInfo.JpmsModuleAccessProblem
import com.intellij.java.codeserver.core.JpmsModuleInfo
import com.intellij.java.codeserver.core.JpmsModuleInfo.TargetModuleInfo
import com.intellij.psi.*
import org.jetbrains.annotations.Nls

/**
 * Checks package accessibility according to JLS 7 "Packages and Modules".
 *
 * @see <a href="https://docs.oracle.com/javase/specs/jls/se9/html/jls-7.html">JLS 7 "Packages and Modules"</a>
 * @see <a href="http://openjdk.org/jeps/261">JEP 261: Module System</a>
 */
internal class JavaPlatformModuleSystem : JavaModuleSystemEx {
  override fun getName(): String = JavaBundle.message("java.platform.module.system.name")

  override fun isAccessible(targetPackageName: String, targetFile: PsiFile?, place: PsiElement): Boolean {
    val useFile = place.containingFile?.originalFile ?: return true
    val infos = JpmsModuleInfo.findTargetModuleInfos(targetPackageName, targetFile, useFile) ?: return true
    return infos.isNotEmpty() && infos.any { info -> info.accessAt(useFile).checkAccess(useFile, JpmsModuleAccessMode.EXPORT) == null }
  }

  override fun checkAccess(targetPackageName: String, targetFile: PsiFile?, place: PsiElement): ErrorWithFixes? {
    val useFile = place.containingFile?.originalFile ?: return null
    val infos = JpmsModuleInfo.findTargetModuleInfos(targetPackageName, targetFile, useFile) ?: return null
    if (infos.isEmpty()) {
      return ErrorWithFixes(JavaErrorBundle.message("package.not.found", targetPackageName))
    }
    var error: ErrorWithFixes? = null
    for (info in infos) {
      val moduleAccessInfo = info.accessAt(useFile)
      val problem = moduleAccessInfo.checkAccess(useFile, JpmsModuleAccessMode.READ)
      if (problem == null) return null
      if (error == null) {
        error = moduleAccessInfo.toErrorWithFixes(problem, place)
      }
    }
    return error 
  }

  override fun isAccessible(targetModule: PsiJavaModule, place: PsiElement): Boolean {
    val useFile = place.containingFile?.originalFile ?: return true
    return TargetModuleInfo(targetModule, "").accessAt(useFile).checkModuleAccess(place) == null
  }

  fun JpmsModuleAccessInfo.toErrorWithFixes(problem: JpmsModuleAccessProblem, place: PsiElement): ErrorWithFixes {
    return ErrorWithFixes(getMessage(problem), getFixes(problem, place))
  }

  private fun JpmsModuleAccessInfo.getMessage(problem: JpmsModuleAccessProblem): @Nls String {
    val current = current
    val target = target
    val targetModule = target.module
    return when (problem) {
      JpmsModuleAccessProblem.FROM_NAMED ->
        JavaErrorBundle.message("module.access.from.named", target.packageName, targetModule!!.name, current.name)
      JpmsModuleAccessProblem.FROM_UNNAMED -> JavaErrorBundle.message("module.access.from.unnamed", target.packageName, targetModule!!.name)
      JpmsModuleAccessProblem.TO_UNNAMED -> JavaErrorBundle.message("module.access.to.unnamed", target.packageName, current.name)
      JpmsModuleAccessProblem.PACKAGE_BAD_NAME -> JavaErrorBundle.message("module.access.bad.name", target.packageName, targetModule!!.name)
      JpmsModuleAccessProblem.BAD_NAME -> JavaErrorBundle.message("module.bad.name", targetModule!!.name)
      JpmsModuleAccessProblem.NOT_IN_GRAPH -> JavaErrorBundle.message("module.not.in.graph", targetModule!!.name)
      JpmsModuleAccessProblem.PACKAGE_NOT_IN_GRAPH -> JavaErrorBundle.message("module.access.not.in.graph", target.packageName, targetModule!!.name)
      JpmsModuleAccessProblem.DOES_NOT_READ -> JavaErrorBundle.message("module.does.not.read", targetModule!!.name, current.name)
      JpmsModuleAccessProblem.PACKAGE_DOES_NOT_READ -> JavaErrorBundle.message("module.access.does.not.read", target.packageName, targetModule!!.name, current.name)
      JpmsModuleAccessProblem.JPS_DEPENDENCY_PROBLEM -> "-" // TODO: proper name?
    }
  }

  private fun JpmsModuleAccessInfo.getFixes(
    problem: JpmsModuleAccessProblem,
    place: PsiElement
  ): List<IntentionAction> {
    val currentJpsModule = current.jpsModule
    val targetModule = target.module
    return when (problem) {
      JpmsModuleAccessProblem.FROM_UNNAMED, JpmsModuleAccessProblem.FROM_NAMED -> {
        when {
          target.packageName.isEmpty() -> emptyList()
          targetModule is PsiCompiledElement && currentJpsModule != null ->
            listOf(AddExportsOptionFix(currentJpsModule, targetModule.name, target.packageName,
                                       current.name).asIntention())
          targetModule !is PsiCompiledElement && current.module != null ->
            listOf(AddExportsDirectiveFix(targetModule!!, target.packageName, current.name).asIntention())
          else -> emptyList()
        }
      }
      JpmsModuleAccessProblem.TO_UNNAMED, JpmsModuleAccessProblem.PACKAGE_BAD_NAME, JpmsModuleAccessProblem.BAD_NAME -> listOf()
      JpmsModuleAccessProblem.PACKAGE_NOT_IN_GRAPH, JpmsModuleAccessProblem.NOT_IN_GRAPH ->
        listOf(AddModulesOptionFix(currentJpsModule!!, targetModule!!.name).asIntention())
      JpmsModuleAccessProblem.PACKAGE_DOES_NOT_READ, JpmsModuleAccessProblem.DOES_NOT_READ ->
        listOf(AddRequiresDirectiveFix(current.module!!, targetModule!!.name).asIntention())
      JpmsModuleAccessProblem.JPS_DEPENDENCY_PROBLEM -> {
        val reference: PsiJavaModuleReference = (place as? PsiJavaModuleReferenceElement)?.reference ?: return listOf()
        val registrar: MutableList<IntentionAction> = ArrayList()
        QuickFixFactory.getInstance().registerOrderEntryFixes(reference, registrar)
        registrar
      }
    }
  }}