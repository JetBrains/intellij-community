// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.deadCode

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.daemon.ImplicitUsageProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField

class EasyMockImplicitUsageProvider : ImplicitUsageProvider {
  override fun isImplicitUsage(element: PsiElement): Boolean = false

  override fun isImplicitRead(element: PsiElement): Boolean = false

  override fun isImplicitWrite(element: PsiElement): Boolean {
    return element is PsiField && AnnotationUtil.isAnnotated(element, "org.easymock.Mock", 0)
  }

  override fun isImplicitlyNotNullInitialized(element: PsiElement): Boolean = isImplicitWrite(element)
}