// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.logging

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement

class UnspecifiedLogger : JvmLogger {
  override val loggerTypeName: String = "Unspecified"
  override val priority: Int = 100

  override fun isOnlyOnStartup() = true
  override fun insertLoggerAtClass(project: Project,
                                   clazz: PsiClass,
                                   logger: PsiElement): PsiElement = throw UnsupportedOperationException()

  override fun isAvailable(project: Project?): Boolean = throw UnsupportedOperationException()

  override fun isAvailable(module: Module?): Boolean = throw UnsupportedOperationException()

  override fun createLoggerElementText(project: Project, clazz: PsiClass): PsiElement = throw UnsupportedOperationException()

  override fun toString(): String = UNSPECIFIED_LOGGER_NAME

  companion object {
    const val UNSPECIFIED_LOGGER_NAME: String = "Unspecified"
  }
}