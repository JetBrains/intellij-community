// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.logging

import com.intellij.logging.UnspecifiedLogger.Companion.UNSPECIFIED_LOGGER_NAME
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement

interface JvmLogger {
  val loggerTypeName: String

  val priority: Int

  fun isOnlyOnStartup() = false

  fun insertLoggerAtClass(project: Project, clazz: PsiClass): PsiElement? {
    val logger = createLoggerElementText(project, clazz) ?: return null
    return WriteAction.compute<PsiElement?, Exception> {
      insertLoggerAtClass(project, clazz, logger)
    }
  }

  fun insertLoggerAtClass(project: Project, clazz: PsiClass, logger: PsiElement): PsiElement?

  fun isAvailable(project: Project?) : Boolean

  fun isAvailable(module: Module?) : Boolean

  fun isPossibleToPlaceLoggerAtClass(clazz: PsiClass) : Boolean

  fun createLoggerElementText(project: Project, clazz: PsiClass): PsiElement?

  companion object {
    private val EP_NAME = ExtensionPointName<JvmLogger>("com.intellij.jvm.logging")

    fun getAllLoggersNames(isOnlyOnStartup: Boolean): List<String> {
      return getAllLoggers(isOnlyOnStartup).map { it.toString() }
    }

    fun getAllLoggers(isOnlyOnStartup: Boolean): List<JvmLogger> {
      return EP_NAME.extensionList.filter { if (!isOnlyOnStartup) !it.isOnlyOnStartup() else true }.sortedByDescending { it.priority }
    }

    fun getLoggerByName(loggerName: String?): JvmLogger? {
      if (loggerName == UNSPECIFIED_LOGGER_NAME) return null
      return EP_NAME.extensionList.find { it.toString() == loggerName }
    }
  }
}
