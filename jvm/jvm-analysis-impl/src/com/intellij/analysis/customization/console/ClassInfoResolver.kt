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

class ClassInfoResolver(val project: Project, private val mySearchScope: GlobalSearchScope) {

  companion object {
    internal fun findSubclassName(className: String): String? {
      val probablySubclassIndex = className.lastIndexOf('$')
      if (probablySubclassIndex != -1 && probablySubclassIndex != 0 && className.length > probablySubclassIndex + 1) {
        return className.substring(probablySubclassIndex + 1)
      }
      return null
    }

    private fun findClasses(project: Project,
                            scope: GlobalSearchScope,
                            shortClassName: String,
                            targetPackageName: String): List<PsiClass> {
      val result = mutableSetOf<PsiClass>()
      val list = PsiShortNamesCache.EP_NAME.getExtensionList(project)
      for (cache in list) {
        val classes = cache.getClassesByName(shortClassName, scope)
        for (clazz in classes) {
          val qualifiedName = clazz.qualifiedName
          if (!canBeShortenedPackages(qualifiedName, targetPackageName) &&
              !(targetPackageName.contains("$") &&
                canBeShortenedPackages(qualifiedName?.replace('$', '.'), targetPackageName.replace('$', '.')))) {
            continue
          }
          result.add(clazz)
        }
      }
      if (result.isEmpty()) {
        var newShortClassName = findSubclassName(shortClassName)
        if (newShortClassName?.isNotBlank() == true) {
          if (!newShortClassName[0].isDigit()) {
            val newTargetPackageName = "$targetPackageName." +
                                       shortClassName.substring(0, shortClassName.length - newShortClassName.length - 1)
            return findClasses(project, scope, newShortClassName, newTargetPackageName)
          }
          else {
            newShortClassName = shortClassName.take(shortClassName.length - newShortClassName.length - 1)
            return findClasses(project, scope, newShortClassName, targetPackageName)
          }
        }
      }
      return result.toList()
    }

    /**
     * @param qualifiedName The class qualified name to check.
     * @param targetPackageName The target package name.
     * @return True, if clazz package can be shortened to targetPackageName, false otherwise.
     * There are two popular ways to shorten:
     * 1. Keep only n last characters of the package: aaa.bbb.ccc -> b.ccc
     * 2. Keep only the first n characters of each directory: abc.bcd.cef -> a.b.c
     */
    private fun canBeShortenedPackages(qualifiedName: String?, targetPackageName: String): Boolean {
      if (qualifiedName == null) return false
      val actualPackageName = StringUtil.getPackageName(qualifiedName)
      if (actualPackageName.endsWith(targetPackageName)) return true
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

  fun resolveClasses(className: String, packageName: String): ClassResolveInfo {
    if (isDumb(project)) {
      return ClassResolveInfo.EMPTY
    }

    val classes = findClasses(project, mySearchScope, className, packageName)

    val mapWithClasses = classes.filter { it.isValid && it.containingFile != null }
      .map { Pair(it, it.containingFile.virtualFile) }
      .filter { it.second != null }
      .toMap()

    val result = if (mapWithClasses.isEmpty()) ClassResolveInfo.EMPTY else ClassResolveInfo(mapWithClasses)
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
