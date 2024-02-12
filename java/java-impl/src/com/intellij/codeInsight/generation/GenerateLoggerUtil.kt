// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation

import com.intellij.lang.logging.JvmLogger
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiImplicitClass
import com.intellij.psi.util.parentsOfType

object GenerateLoggerUtil {
  fun findSuitableLoggers(module: Module?, filterByImportExclusion : Boolean = false): List<JvmLogger> {
    return JvmLogger.getAllLoggers(false).filter {
      it.isAvailable(module) && !(filterByImportExclusion && it.isExcludedFromImport(module?.project))
    }
  }

  fun getAllNestedClasses(element: PsiElement) = element.parentsOfType(PsiClass::class.java, true)
    .filter { clazz -> clazz !is PsiAnonymousClass && clazz !is PsiImplicitClass }

  fun getPossiblePlacesForLogger(element: PsiElement, loggerList: List<JvmLogger>): List<PsiClass> = getAllNestedClasses(element)
    .filter { clazz -> isPossibleToPlaceLogger(clazz, loggerList) }
    .toList()
    .reversed()

  private fun isPossibleToPlaceLogger(psiClass: PsiClass, loggerList: List<JvmLogger>): Boolean = loggerList.all {
    it.isPossibleToPlaceLoggerAtClass(psiClass)
  }
}