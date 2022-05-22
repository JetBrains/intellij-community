// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.uast.UCallExpression

interface TestFailedLineManager {
  fun getTestInfo(call: UCallExpression): TestInfo?

  fun ruConfigurationQuickFix(element: PsiElement): LocalQuickFix?

  fun debugConfigurationQuickFix(element: PsiElement, topStacktraceLine: String): LocalQuickFix?

  interface TestInfo {
    val errorMessage: String

    val topStacktraceLine: String
  }

  companion object {
    fun getInstance(project: Project): TestFailedLineManager = project.getService(TestFailedLineManager::class.java)
  }
}