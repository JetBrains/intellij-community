// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.logging

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.PsiUtil

abstract class JvmLogger(
  val loggerName: String,
  protected val factoryName: String,
  protected val methodName: String,
  private val classNamePattern: String,
  protected val priority: Int,
) {
  open fun isOnlyOnStartup() = false

  fun insertLoggerAtClass(project: Project, clazz: PsiClass): PsiElement? {
    val logger = createLoggerElementText(project, clazz) as? PsiField ?: return null
    return WriteAction.compute<PsiElement?, Exception> {
      insertLoggerAtClass(project, clazz, logger)
    }
  }

  protected open fun insertLoggerAtClass(project: Project, clazz: PsiClass, logger: PsiElement): PsiElement? {
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(logger)
    return clazz.add(logger)
  }

  protected open fun createLoggerElementText(project: Project, clazz: PsiClass): PsiElement? {
    val factory = JavaPsiFacade.getElementFactory(project)
    val className = clazz.name ?: return null
    val fieldText = "$loggerName ${LOGGER_IDENTIFIER} = ${factoryName}.$methodName(${String.format(classNamePattern, className)});"
    return factory.createFieldFromText(fieldText, clazz).apply {
      PsiUtil.setModifierProperty(this, PsiModifier.STATIC, true)
      PsiUtil.setModifierProperty(this, PsiModifier.FINAL, true)
      PsiUtil.setModifierProperty(this, PsiModifier.PRIVATE, true)
    }
  }

  companion object {
    const val LOGGER_IDENTIFIER = "LOGGER"
    const val UNSPECIFIED_LOGGER_NAME = "Unspecified"

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
