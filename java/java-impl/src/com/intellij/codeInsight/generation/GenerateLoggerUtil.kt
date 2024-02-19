// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation

import com.intellij.codeInsight.completion.JavaCompletionUtil
import com.intellij.lang.logging.JvmLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.parentsOfType

object GenerateLoggerUtil {
  fun findSuitableLoggers(module: Module?, filterByImportExclusion: Boolean = false): List<JvmLogger> {
    val project = module?.project ?: return emptyList()
    return JvmLogger.getAllLoggers(false).filter {
      it.isAvailable(module) && !(filterByImportExclusion && isLoggerExcluded(project, it))
    }
  }

  private fun isLoggerExcluded(project: Project, logger: JvmLogger): Boolean {
    val clazz = JavaPsiFacade.getInstance(project).findClass(logger.loggerTypeName, GlobalSearchScope.everythingScope(project))
                ?: return true
    return JavaCompletionUtil.isInExcludedPackage(clazz, false)
  }

  fun getAllNestedClasses(element: PsiElement): Sequence<PsiClass> = element.parentsOfType(PsiClass::class.java, true)
    .filter { clazz -> clazz !is PsiAnonymousClass && clazz !is PsiImplicitClass }

  fun getPossiblePlacesForLogger(element: PsiElement, loggerList: List<JvmLogger>): List<PsiClass> = getAllNestedClasses(element)
    .filter { clazz -> isPossibleToPlaceLogger(clazz, loggerList) }
    .toList()
    .reversed()

  private fun isPossibleToPlaceLogger(psiClass: PsiClass, loggerList: List<JvmLogger>): Boolean = loggerList.all {
    it.isPossibleToPlaceLoggerAtClass(psiClass)
  }
}