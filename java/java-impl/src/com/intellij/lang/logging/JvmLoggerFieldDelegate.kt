// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.logging

import com.intellij.java.library.JavaLibraryUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.PsiUtil
import com.intellij.ui.logging.JvmLoggingSettingsStorage

/**
 * Represents a delegate implementation of the JvmLogger interface that is used to insert loggers which are [PsiField].
 *
 * @property factoryName The name of the factory class used to create the logger.
 * @property methodName The name of the method in the factory class used to get the logger.
 * @property classNamePattern The pattern used to format the class name when creating the logger element.
 * @property loggerTypeName The fully qualified name of the logger's type.
 * @property priority The priority of the logger, used for ordering in the settings.
 */
public class JvmLoggerFieldDelegate(
  private val factoryName: String,
  private val methodName: String,
  private val classNamePattern: String,
  override val id: String,
  override val loggerTypeName: String,
  override val priority: Int,
) : JvmLogger {
  override fun insertLoggerAtClass(project: Project, clazz: PsiClass, logger: PsiElement): PsiElement? {
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(logger)
    return clazz.add(logger)
  }

  override fun isAvailable(project: Project?): Boolean {
    return project != null
           && JavaPsiFacade.getInstance(project).findClass(loggerTypeName, ProjectContainingLibrariesScope.getScope(project)) != null
  }

  override fun isAvailable(module: Module?): Boolean = JavaLibraryUtil.hasLibraryClass(module, loggerTypeName)

  override fun isPossibleToPlaceLoggerAtClass(clazz: PsiClass): Boolean {
    val resolveHelper = JavaPsiFacade.getInstance(clazz.project).resolveHelper
    val settings = clazz.project.service<JvmLoggingSettingsStorage>().state
    return clazz.allFields.any {
      resolveHelper.isAccessible(it, clazz, null) &&
      (it.name == settings.loggerName || it.type.canonicalText == loggerTypeName)
    }.not()
  }

  override fun createLogger(project: Project, clazz: PsiClass): PsiField? {
    val factory = JavaPsiFacade.getElementFactory(project)
    val className = clazz.name ?: return null
    val settings = clazz.project.service<JvmLoggingSettingsStorage>().state
    val fieldText = "$loggerTypeName ${settings.loggerName} = ${factoryName}.$methodName(${
      String.format(classNamePattern, className)
    });"
    return factory.createFieldFromText(fieldText, clazz).apply {
      PsiUtil.setModifierProperty(this, PsiModifier.STATIC, true)
      PsiUtil.setModifierProperty(this, PsiModifier.FINAL, true)
      PsiUtil.setModifierProperty(this, PsiModifier.PRIVATE, true)
    }
  }

  override fun getLogFieldName(clazz: PsiClass): String? {
    val settings = clazz.project.service<JvmLoggingSettingsStorage>().state
    return settings.loggerName
  }

  public companion object {
    public const val LOGGER_IDENTIFIER: String = "log"
  }
}