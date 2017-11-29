/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import com.intellij.psi.util.PsiUtil

class JavaPlatformModuleSystem : JavaModuleSystemEx {
  override fun getName() = "Java Platform Module System"

  override fun isAccessible(target: PsiPackage, place: PsiElement) = checkAccess(target, place, true) == null
  override fun isAccessible(target: PsiClass, place: PsiElement) = checkAccess(target, place, true) == null

  override fun checkAccess(target: PsiPackage, place: PsiElement) = checkAccess(target, place, false)
  override fun checkAccess(target: PsiClass, place: PsiElement) = checkAccess(target, place, false)

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
          return if (error == null ||
                     dirs.size > 1 && dirs.asSequence().drop(1).any { checkAccess(it, useFile, target.qualifiedName, true) == null }) null
                 else error
        }
      }
    }

    return null
  }

  private val ERR = ErrorWithFixes("-")

  private fun checkAccess(target: PsiFileSystemItem, place: PsiFileSystemItem, packageName: String, quick: Boolean): ErrorWithFixes? {
    val targetModule = JavaModuleGraphUtil.findDescriptorByElement(target)?.originalElement as PsiJavaModule?
    val useModule = JavaModuleGraphUtil.findDescriptorByElement(place)?.originalElement as PsiJavaModule?

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
          useModule == null -> ErrorWithFixes(JavaErrorMessages.message("module.access.from.unnamed", packageName, targetModule.name))
          else -> ErrorWithFixes(JavaErrorMessages.message("module.access.from.named", packageName, targetModule.name, useModule.name))
        }
      }
      if (useModule == null) {
        return null
      }
      if (!(targetModule.name == PsiJavaModule.JAVA_BASE || JavaModuleGraphUtil.reads(useModule, targetModule))) {
        return if (quick) ERR else ErrorWithFixes(
          JavaErrorMessages.message("module.access.does.not.read", packageName, targetModule.name, useModule.name),
          listOf(AddRequiredModuleFix(useModule, targetModule.name)))
      }
    }
    else if (useModule != null) {
      return if (quick) ERR else ErrorWithFixes(JavaErrorMessages.message("module.access.to.unnamed", packageName, useModule.name))
    }

    return null
  }
}