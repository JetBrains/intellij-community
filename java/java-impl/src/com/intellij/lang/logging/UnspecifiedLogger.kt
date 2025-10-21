// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.logging

import com.intellij.java.JavaBundle
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement

/**
 * This class represents logger which user has after creation of the project. This is fake logger which is only necessary to support
 * "unspecified" state, e.g. where there is no preferred logger selected.
 */
public class UnspecifiedLogger : JvmLogger {
  override val id: String = UNSPECIFIED_LOGGER_ID
  override val loggerTypeName: String = "Unspecified"
  override val priority: Int = 1000

  override fun isOnlyOnStartup(): Boolean = true
  override fun insertLoggerAtClass(project: Project,
                                   clazz: PsiClass,
                                   logger: PsiElement): PsiElement = throw UnsupportedOperationException()

  override fun isAvailable(project: Project?): Boolean = false

  override fun isAvailable(module: Module?): Boolean = false

  override fun isPossibleToPlaceLoggerAtClass(clazz: PsiClass): Boolean = false

  override fun createLogger(project: Project, clazz: PsiClass): PsiElement = throw UnsupportedOperationException()

  override fun getLogFieldName(clazz: PsiClass): String? = null

  override fun toString(): String = JavaBundle.message("java.configurable.logger.unspecified")

  public companion object {
    public const val UNSPECIFIED_LOGGER_ID: String = "Unspecified"
  }
}