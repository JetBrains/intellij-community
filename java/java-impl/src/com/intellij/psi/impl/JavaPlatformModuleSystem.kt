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
import com.intellij.codeInsight.daemon.JavaErrorMessages
import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil
import com.intellij.codeInsight.daemon.impl.quickfix.AddRequiredModuleFix
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Pair
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

  private fun checkAccess(target: PsiClass, place: PsiElement, quick: Boolean): Pair<String, List<IntentionAction>>? {
    if (PsiUtil.isLanguageLevel9OrHigher(place)) {
      val targetFile = target.containingFile
      if (targetFile is PsiClassOwner) {
        return checkAccess(targetFile, place, targetFile.packageName, quick)
      }
    }

    return null
  }

  private fun checkAccess(target: PsiPackage, place: PsiElement, quick: Boolean): Pair<String, List<IntentionAction>>? {
    if (place.parent !is PsiJavaCodeReferenceElement) {
      val useFile = place.containingFile
      if (useFile != null && PsiUtil.isLanguageLevel9OrHigher(useFile)) {
        val useVFile = useFile.virtualFile
        if (useVFile != null) {
          val index = ProjectFileIndex.getInstance(useFile.project)
          val module = index.getModuleForFile(useVFile)
          if (module != null) {
            val test = index.isInTestSourceContent(useVFile)
            val dirs = target.getDirectories(module.getModuleWithDependenciesAndLibrariesScope(test))
            if (dirs.size == 1) {
              return checkAccess(dirs[0], place, target.qualifiedName, quick)
            }
          }
        }
      }
    }

    return null
  }

  private val ERR = Pair("-", emptyList<IntentionAction>())

  private fun checkAccess(target: PsiFileSystemItem, place: PsiElement, packageName: String, quick: Boolean): Pair<String, List<IntentionAction>>? {
    val targetModule = JavaModuleGraphUtil.findDescriptorByElement(target)
    val useModule = JavaModuleGraphUtil.findDescriptorByElement(place)

    if (targetModule != null) {
      if (targetModule == useModule) {
        return null
      }
      if (useModule == null && targetModule.containingFile?.virtualFile?.fileSystem !is JrtFileSystem) {
        return null  // a target is not on the mandatory module path
      }
      if (!(targetModule is LightJavaModule || JavaModuleGraphUtil.exports(targetModule, packageName, useModule))) {
        return if (quick) ERR
          else if (useModule == null) Pair(JavaErrorMessages.message("module.access.from.unnamed", packageName, targetModule.name), emptyList())
          else Pair(JavaErrorMessages.message("module.access.from.named", packageName, targetModule.name, useModule.name), emptyList())
      }
      if (useModule == null) {
        return null
      }
      if (!(useModule.name == PsiJavaModule.JAVA_BASE || JavaModuleGraphUtil.reads(useModule, targetModule))) {
        return if (quick) ERR else Pair(
          JavaErrorMessages.message("module.access.does.not.read", packageName, targetModule.name, useModule.name),
          listOf(AddRequiredModuleFix(useModule, targetModule.name)))
      }
    }
    else if (useModule != null) {
      return if (quick) ERR else Pair(JavaErrorMessages.message("module.access.to.unnamed", packageName, useModule.name), emptyList())
    }

    return null
  }
}