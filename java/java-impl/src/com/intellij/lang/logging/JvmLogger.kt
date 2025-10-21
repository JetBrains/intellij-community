// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.logging

import com.intellij.codeInsight.completion.JavaCompletionUtil
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.parentsOfType
import org.jetbrains.annotations.ApiStatus

/**
 * Extension point representing a JVM logger. Extensions of this EP are used to store information about concrete logger and provide
 * the way to generate a logger at the class. Please, don't use it now, this API will be rewritten in the future.
 */
@ApiStatus.Internal
public interface JvmLogger {
  /**
   * This field represents id of the logger which is used to save the logger the settings
   */
  public val id : String
  /**
   * This field represents fully qualified name of the logger's type
   */
  public val loggerTypeName: String

  /**
   * This field is used to determine the order of loggers in the settings
   * @see com.intellij.ui.logging.JvmLoggingConfigurable
   */
  public val priority: Int

  /**
   * Determines if the logger should only be used when user didn't specify the preferred logger in the settings.
   * For example, it happens after creation of the new project.
   *
   * @return true if the logger should only be used during startup, false otherwise
   * @see UnspecifiedLogger
   */
  public fun isOnlyOnStartup(): Boolean = false

  /**
   * Method for inserting the logger at the specified class. Should only be invoked inside WriteAction.
   * @param project the project context
   * @param clazz the class where the logger element will be inserted
   * @param logger PsiElement, corresponding to the logger to be inserted
   */
  public fun insertLoggerAtClass(project: Project, clazz: PsiClass, logger: PsiElement): PsiElement?

  /**
   * Determines if the logger is available for the given project. Should only be invoked inside ReadAction.
   *
   * @param project the project context
   * @return true if the logger is available, false otherwise
   */
  public fun isAvailable(project: Project?): Boolean

  /**
   * Determines if the logger is available for the given module. Should only be invoked inside ReadAction.
   *
   * @param module the module context
   * @return true if the logger is available, false otherwise
   */
  public fun isAvailable(module: Module?): Boolean

  /**
   * Determines if it is possible to place a logger at the specified class.
   *
   * @param clazz the class where the logger will be placed
   * @return true if it is possible to place a logger, false otherwise
   */
  public fun isPossibleToPlaceLoggerAtClass(clazz: PsiClass): Boolean

  /**
   * Creates a logger element for inserting into a class.
   *
   * @param project the project context
   * @param clazz the class where the logger element will be inserted
   * @return the created logger element, or null if creation fails
   */
  public fun createLogger(project: Project, clazz: PsiClass): PsiElement?

  /**
   * @return the name of the log field for the class, or null if it is impossible to define
   */
  public fun getLogFieldName(clazz: PsiClass): String?

  public companion object {
    private val EP_NAME = ExtensionPointName<JvmLogger>("com.intellij.jvm.logging")

    /**
     * Retrieves a list of JvmLogger instances.
     *
     * @param isOnlyOnStartup flag indicating whether to include only loggers that should be used on startup like [UnspecifiedLogger].
     * @return a list of JvmLogger instances sorted by priority
     */
    public fun getAllLoggers(isOnlyOnStartup: Boolean): List<JvmLogger> {
      return EP_NAME.extensionList.filter { if (!isOnlyOnStartup) !it.isOnlyOnStartup() else true }.sortedByDescending { it.priority }
    }

    /**
     * Retrieves a logger by its ID.
     *
     * @param loggerId The ID of the logger to retrieve.
     * @return The logger with the specified ID, or null if not found.
     */
    public fun getLoggerById(loggerId: String?): JvmLogger? {
      return EP_NAME.extensionList.find { loggerId == it.id }
    }

    /**
     * Finds the suitable loggers for the given module.
     *
     * @param module the module context
     * @param filterByImportExclusion indicates whether to filter loggers by import exclusion
     * @return a list of suitable loggers for the module
     */
    public fun findSuitableLoggers(module: Module?, filterByImportExclusion: Boolean = false): List<JvmLogger> {
      val project = module?.project ?: return emptyList()
      return getAllLoggers(false).filter {
        it.isAvailable(module) && !(filterByImportExclusion && isLoggerExcluded(project, it))
      }
    }

    private fun isLoggerExcluded(project: Project, logger: JvmLogger): Boolean {
      val clazz = JavaPsiFacade.getInstance(project).findClass(logger.loggerTypeName, GlobalSearchScope.everythingScope(project))
                  ?: return true
      return JavaCompletionUtil.isInExcludedPackage(clazz, false)
    }

    /**
     * Retrieves all the nested classes sorted by their depth in the place where the [PsiElement] is located.
     *
     * @param element the [PsiElement] from which to retrieve the nested classes
     * @return [Sequence] of [PsiClass] representing the nested classes
     */
    public fun getAllNamedContainingClasses(element: PsiElement): List<PsiClass> = element.parentsOfType(PsiClass::class.java, true)
      .filter { clazz -> clazz !is PsiAnonymousClass && clazz !is PsiImplicitClass }
      .toList()

    /**
     * Retrieves the possible places for inserting a logger element based on the given [element] and [loggerList].
     *
     * @param element the PsiElement indicating the current position for inserting a logger
     * @param loggerList the list of JvmLogger objects representing the available loggers
     * @return a list of PsiClass objects representing the possible places for inserting a logger, in reversed order
     * @see isOnlyOnStartup
     */
    public fun getPossiblePlacesForLogger(element: PsiElement, loggerList: List<JvmLogger>): List<PsiClass> = getAllNamedContainingClasses(element)
      .filter { clazz -> isPossibleToPlaceLogger(clazz, loggerList) }
      .reversed()

    private fun isPossibleToPlaceLogger(psiClass: PsiClass, loggerList: List<JvmLogger>): Boolean = loggerList.all {
      it.isPossibleToPlaceLoggerAtClass(psiClass)
    }
  }
}
