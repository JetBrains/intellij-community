// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.deadCode

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.daemon.ImplicitUsageProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField

private const val ORG_ASSERTJ_CORE_API_JUNIT_JUPITER_INJECTSOFTASSERTIONS = "org.assertj.core.api.junit.jupiter.InjectSoftAssertions"

class AssertJImplicitUsageProvider : ImplicitUsageProvider {
  override fun isImplicitUsage(element: PsiElement): Boolean = false

  override fun isImplicitRead(element: PsiElement): Boolean = false

  override fun isImplicitWrite(element: PsiElement): Boolean {
    return element is PsiField && AnnotationUtil.isAnnotated(element, ORG_ASSERTJ_CORE_API_JUNIT_JUPITER_INJECTSOFTASSERTIONS, 0)
  }

  override fun isImplicitlyNotNullInitialized(element: PsiElement): Boolean = isImplicitWrite(element)
}