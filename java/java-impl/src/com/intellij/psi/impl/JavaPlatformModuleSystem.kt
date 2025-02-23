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
import com.intellij.java.codeserver.core.JpmsModuleAccessInfo.JpmsModuleAccessProblem
import com.intellij.java.codeserver.core.JpmsModuleInfo.TargetModuleInfo
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.pom.java.JavaFeature
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtil
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
    return getProblem(targetPackageName, targetFile, place, true) { it.isExported() } == null
  }

  override fun checkAccess(targetPackageName: String, targetFile: PsiFile?, place: PsiElement): ErrorWithFixes? {
    return getProblem(targetPackageName, targetFile, place, false) { it.isAccessible() }
  }

  override fun isAccessible(targetModule: PsiJavaModule, place: PsiElement): Boolean {
    val useFile = place.containingFile?.originalFile ?: return true
    return TargetModuleInfo(targetModule, "").accessAt(useFile).checkModuleAccess(place) == null
  }

  override fun checkAccess(targetModule: PsiJavaModule, place: PsiElement): ErrorWithFixes? {
    val useFile = place.containingFile?.originalFile ?: return null
    val moduleAccess = TargetModuleInfo(targetModule, "").accessAt(useFile)

    val access = moduleAccess.checkModuleAccess(place)
    return if (access == null) null
    else moduleAccess.toErrorWithFixes(access, place)
  }

  private fun getProblem(targetPackageName: String, targetFile: PsiFile?, place: PsiElement, quick: Boolean,
                         isAccessible: (JpmsModuleAccessInfo) -> Boolean): ErrorWithFixes? {
    val originalTargetFile = targetFile?.originalFile
    val useFile = place.containingFile?.originalFile ?: return null
    if (!PsiUtil.isAvailable(JavaFeature.MODULES, useFile)) return null

    val useVFile = useFile.virtualFile
    val index = ProjectFileIndex.getInstance(useFile.project)
    if (useVFile != null && index.isInLibrarySource(useVFile)) return null
    if (originalTargetFile != null && originalTargetFile.isPhysical) {
      val target = TargetModuleInfo(originalTargetFile, targetPackageName)
      return checkAccess(target, useFile, quick, isAccessible)
    }
    if (useVFile == null) return null

    val target = JavaPsiFacade.getInstance(useFile.project).findPackage(targetPackageName) ?: return null
    val module = index.getModuleForFile(useVFile) ?: return null
    val test = index.isInTestSourceContent(useVFile)
    val moduleScope = module.getModuleWithDependenciesAndLibrariesScope(test)
    val dirs = target.getDirectories(moduleScope)
    if (dirs.isEmpty()) {
      return if (target.getFiles(moduleScope).isEmpty()) {
        ErrorWithFixes(JavaErrorBundle.message("package.not.found", target.qualifiedName))
      }
      else {
        null
      }
    }

    val error = checkAccess(TargetModuleInfo(dirs[0], target.qualifiedName), useFile, quick, isAccessible) ?: return null
    return when {
      dirs.size == 1 -> error
      dirs.asSequence().drop(1).any { checkAccess(TargetModuleInfo(it, target.qualifiedName), useFile, true, isAccessible) == null } -> null
      else -> error
    }
  }

  private val ERR = ErrorWithFixes("-")

  private fun checkAccess(target: TargetModuleInfo, place: PsiFileSystemItem, quick: Boolean,
                          isAccessible: (JpmsModuleAccessInfo) -> Boolean): ErrorWithFixes? {
    val moduleAccess = target.accessAt(place)

    val access = moduleAccess.checkAccess(place, isAccessible)
    return when {
      access == null -> null
      quick -> ERR
      else -> moduleAccess.toErrorWithFixes(access, place)
    }
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