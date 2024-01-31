// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.logging

import com.intellij.logging.UnspecifiedLogger.Companion.UNSPECIFIED_LOGGER_NAME
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement

/**
 * Extension point representing a JVM logger. Extensions of this EP are used to store information about concrete logger and provide
 * the way to generate a logger at the class.
 */
interface JvmLogger {
  /**
   * This field represents fully qualified name of the logger's type
   */
  val loggerTypeName: String

  /**
   * This field is used to determine the order of loggers in the settings
   * @see com.intellij.settings.JvmLoggingConfigurable
   */
  val priority: Int

  /**
   * Determines if the logger should only be used when user didn't specify the preferred logger in the settings.
   * For example, it happens after creation of the new project.
   *
   * @return true if the logger should only be used during startup, false otherwise
   * @see com.intellij.logging.UnspecifiedLogger
   */
  fun isOnlyOnStartup() = false

  /**
   * Inserts a logger element at the specified class.
   *
   * @param project the project context
   * @param clazz the class where the logger element will be inserted
   * @return the inserted logger element, or null if the operation fails
   */
  fun insertLoggerAtClass(project: Project, clazz: PsiClass): PsiElement? {
    val logger = createLogger(project, clazz) ?: return null
    return WriteAction.compute<PsiElement?, Exception> {
      insertLoggerAtClass(project, clazz, logger)
    }
  }

  /**
   * Inner method for inserting the logger at the specified class. Should only be invoked inside WriteAction.
   * @param project the project context
   * @param clazz the class where the logger element will be inserted
   * @param logger PsiElement, corresponding to the logger to be inserted
   */
  fun insertLoggerAtClass(project: Project, clazz: PsiClass, logger: PsiElement): PsiElement?

  /**
   * Determines if the logger is available for the given project.
   *
   * @param project the project context
   * @return true if the logger is available, false otherwise
   */
  fun isAvailable(project: Project?) : Boolean

  /**
   * Determines if the logger is available for the given module.
   *
   * @param module the module context
   * @return true if the logger is available, false otherwise
   */
  fun isAvailable(module: Module?) : Boolean

  /**
   * Determines if it is possible to place a logger at the specified class.
   *
   * @param clazz the class where the logger will be placed
   * @return true if it is possible to place a logger, false otherwise
   */
  fun isPossibleToPlaceLoggerAtClass(clazz: PsiClass) : Boolean

  /**
   * Creates a logger element for inserting into a class.
   *
   * @param project the project context
   * @param clazz the class where the logger element will be inserted
   * @return the created logger element, or null if creation fails
   */
  fun createLogger(project: Project, clazz: PsiClass): PsiElement?

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
