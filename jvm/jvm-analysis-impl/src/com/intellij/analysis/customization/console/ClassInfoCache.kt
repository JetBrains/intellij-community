// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.customization.console

import com.intellij.openapi.project.DumbService.Companion.isDumb
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.util.containers.ContainerUtil

class ClassInfoCache(val project: Project, private val mySearchScope: GlobalSearchScope) {

  companion object {
    private fun findClasses(project: Project,
                            scope: GlobalSearchScope,
                            shortClassName: String,
                            targetPackageName: String): List<PsiClass> {
      val result = mutableSetOf<PsiClass>()
      val list = PsiShortNamesCache.EP_NAME.getExtensionList(project)
      for (cache in list) {
        val classes = cache.getClassesByName(shortClassName, scope)
        for (clazz in classes) {
          if (!canBeShortenedPackages(clazz, targetPackageName)) {
            continue
          }
          result.add(clazz)
        }
      }
      return result.toList()
    }

    private fun canBeShortenedPackages(clazz: PsiClass, targetPackageName: String): Boolean {
      val qualifiedName = clazz.qualifiedName ?: return false
      val actualPackageName = StringUtil.getPackageName(qualifiedName)
      val actualPackageNames = actualPackageName.split(".")
      val targetPackageNames = targetPackageName.split(".")
      if (actualPackageNames.size != targetPackageNames.size) return false
      for (i in actualPackageNames.indices) {
        if (!actualPackageNames[i].startsWith(targetPackageNames[i])) {
          return false
        }
      }
      return true
    }
  }

  private val myCache = ContainerUtil.createConcurrentSoftValueMap<String, ClassResolveInfo>()

  fun resolveClasses(className: String, packageName: String): ClassResolveInfo {
    val key = "$packageName.$className"
    val cached = myCache[key]
    if (cached != null && cached.isValid) {
      return cached
    }

    if (isDumb(project)) {
      return ClassResolveInfo.EMPTY
    }

    val classes = findClasses(project, mySearchScope, className, packageName)

    val mapWithClasses = classes.filter { it.isValid && it.containingFile != null }
      .map { Pair(it, it.containingFile.virtualFile) }
      .filter { it.second != null }
      .toMap()

    val result = if (mapWithClasses.isEmpty()) ClassResolveInfo.EMPTY else ClassResolveInfo(mapWithClasses)
    myCache[key] = result
    return result
  }

  class ClassResolveInfo internal constructor(val classes: Map<PsiClass, VirtualFile>) {

    val isValid: Boolean
      get() = classes.keys.all { obj: PsiElement -> obj.isValid }

    companion object {
      val EMPTY: ClassResolveInfo = ClassResolveInfo(mapOf())
    }
  }
}
