// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl

import com.intellij.codeInsight.JavaModuleSystemEx
import com.intellij.codeInsight.JavaModuleSystemEx.ErrorWithFixes
import com.intellij.codeInsight.daemon.JavaErrorMessages
import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil
import com.intellij.codeInsight.daemon.impl.quickfix.AddRequiredModuleFix
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.jrt.JrtFileSystem
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightJavaModule
import com.intellij.psi.impl.source.PsiJavaModuleReference
import com.intellij.psi.util.PsiUtil

class JavaPlatformModuleSystem : JavaModuleSystemEx {
  override fun getName() = "Java Platform Module System"

  override fun isAccessible(target: PsiPackage, place: PsiElement) = checkAccess(target, place, quick = true) == null
  override fun isAccessible(target: PsiClass, place: PsiElement) = checkAccess(target, place, quick = true) == null

  override fun checkAccess(target: PsiPackage, place: PsiElement) = checkAccess(target, place, quick = false)
  override fun checkAccess(target: PsiClass, place: PsiElement) = checkAccess(target, place, quick = false)

  private fun checkAccess(target: PsiClass, place: PsiElement, quick: Boolean): ErrorWithFixes? {
    val useFile = place.containingFile?.originalFile
    if (useFile != null && PsiUtil.isLanguageLevel9OrHigher(useFile)) {
      val targetFile = target.containingFile
      if (targetFile is PsiClassOwner) {
        return checkAccess(targetFile, useFile, targetFile.packageName, quick)
      }
    }

    return null
  }

  private fun checkAccess(target: PsiPackage, place: PsiElement, quick: Boolean): ErrorWithFixes? {
    val useFile = place.containingFile?.originalFile
    if (useFile != null && PsiUtil.isLanguageLevel9OrHigher(useFile)) {
      val useVFile = useFile.virtualFile
      if (useVFile != null) {
        val index = ProjectFileIndex.getInstance(useFile.project)
        val module = index.getModuleForFile(useVFile)
        if (module != null) {
          val test = index.isInTestSourceContent(useVFile)
          val dirs = target.getDirectories(module.getModuleWithDependenciesAndLibrariesScope(test))
          if (dirs.isEmpty()) {
            return if (quick) ERR else ErrorWithFixes(JavaErrorMessages.message("package.not.found", target.qualifiedName))
          }
          val error = checkAccess(dirs[0], useFile, target.qualifiedName, quick)
          return when {
            error == null -> null
            dirs.size == 1 -> error
            dirs.asSequence().drop(1).any { checkAccess(it, useFile, target.qualifiedName, true) == null } -> null
            else -> error
          }
        }
      }
    }

    return null
  }

  private val ERR = ErrorWithFixes("-")

  private fun checkAccess(target: PsiFileSystemItem, place: PsiFileSystemItem, packageName: String, quick: Boolean): ErrorWithFixes? {
    val targetModule = JavaModuleGraphUtil.findDescriptorByElement(target)?.originalElement as PsiJavaModule?
    val targetName = targetModule?.name ?: ""
    val useModule = JavaModuleGraphUtil.findDescriptorByElement(place)?.originalElement as PsiJavaModule?
    val useName = useModule?.name ?: ""

    if (targetModule != null) {
      if (targetModule == useModule) {
        return null
      }

      if (useModule == null && targetModule.containingFile?.virtualFile?.fileSystem !is JrtFileSystem) {
        return null  // a target is not on the mandatory module path
      }

      if (!(targetModule is LightJavaModule || JavaModuleGraphUtil.exports(targetModule, packageName, useModule))) {
        return when {
          quick -> ERR
          useModule == null -> ErrorWithFixes(JavaErrorMessages.message("module.access.from.unnamed", packageName, targetName))
          else -> ErrorWithFixes(JavaErrorMessages.message("module.access.from.named", packageName, targetName, useName))
        }
      }

      if (useModule == null) {
        // JEP 261 "Root modules" (http://openjdk.java.net/jeps/261#root-modules)
        if (!targetName.startsWith("java.")) return null
        val root = PsiJavaModuleReference.resolve(place, "java.se", false)
        if (root == null || JavaModuleGraphUtil.reads(root, targetModule)) return null
        return if (quick) ERR else ErrorWithFixes(JavaErrorMessages.message("module.access.not.in.graph", packageName, targetName))
      }

      if (!(targetName == PsiJavaModule.JAVA_BASE || JavaModuleGraphUtil.reads(useModule, targetModule))) {
        return if (quick) ERR else ErrorWithFixes(
          JavaErrorMessages.message("module.access.does.not.read", packageName, targetName, useName),
          listOf(AddRequiredModuleFix(useModule, targetName)))
      }
    }
    else if (useModule != null) {
      return if (quick) ERR else ErrorWithFixes(JavaErrorMessages.message("module.access.to.unnamed", packageName, useName))
    }

    return null
  }
}