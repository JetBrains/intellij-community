// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.deadCode

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.daemon.ImplicitUsageProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiParameter

private const val ORG_MOCKITO_MOCK = "org.mockito.Mock"

private val INJECTED_FIELD_ANNOTATIONS = listOf(
  ORG_MOCKITO_MOCK,
  "org.mockito.Spy",
  "org.mockito.Captor",
  "org.mockito.InjectMocks"
)

class MockitoImplicitUsageProvider : ImplicitUsageProvider {
  override fun isImplicitUsage(element: PsiElement): Boolean = false

  override fun isImplicitRead(element: PsiElement): Boolean = false

  override fun isImplicitWrite(element: PsiElement): Boolean {
    return element is PsiParameter && AnnotationUtil.isAnnotated(element, ORG_MOCKITO_MOCK, 0)
           || element is PsiField && AnnotationUtil.isAnnotated(element, INJECTED_FIELD_ANNOTATIONS, 0)
  }

  override fun isImplicitlyNotNullInitialized(element: PsiElement): Boolean = isImplicitWrite(element)
}