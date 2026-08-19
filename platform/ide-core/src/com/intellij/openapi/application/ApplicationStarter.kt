// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.ide.CliResult
import com.intellij.openapi.extensions.ExtensionPointName
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class ModernApplicationStarter : ApplicationStarter {
  final override val requiredModality: Int
    get() = ApplicationStarter.NOT_IN_EDT

  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated(message = "use start", level = DeprecationLevel.ERROR)
  final override fun main(args: List<String>): Unit = throw UnsupportedOperationException("Use start(args)")

  abstract suspend fun start(args: List<String>)
}

/**
 * This extension point allows running a custom [command-line] application based on the IntelliJ platform.
 *
 * A command may come directly (there were no other instances of the application running, so the command starts a new one),
 * or externally (the application has detected a running instance and passed a command to it).
 * In the former case, the platform invokes [premain] and [main] methods, in the latter – [processExternalCommandLine].
 */
interface ApplicationStarter {
  companion object {
    const val NON_MODAL: Int = 1
    const val ANY_MODALITY: Int = 2
    const val NOT_IN_EDT: Int = 3

    @ApiStatus.Internal
    const val EP_FQN: String = "com.intellij.appStarter"

    private val EP_NAME = ExtensionPointName<ApplicationStarterEP>(EP_FQN)

    @ApiStatus.Internal
    @JvmStatic
    fun findStarter(key: String): ApplicationStarter? = EP_NAME.findByIdOrFromInstance(key, idGetter = { "no-${key}" })?.get()
  }

  /**
   * Return [ANY_MODALITY] if handling the command requires EDT and can be executed even when there is a modal dialog open.
   * Such a starter may not directly change the PSI/VFS/project model of the opened projects, open new projects,
   * or perform activities that mandate write-safe contexts (see [TransactionGuard]).
   *
   * Return [NOT_IN_EDT] if handling the command can be performed on a background thread (please note that the platform
   * may ignore the flag and process a command as [NON_MODAL]).
   */
  @get:MagicConstant(intValues = [NON_MODAL.toLong(), ANY_MODALITY.toLong(), NOT_IN_EDT.toLong()])
  val requiredModality: Int get() = NON_MODAL

  /**
   * Called before application initialization.
   *
   * @param args program arguments (including the command)
   */
  fun premain(args: List<String>) {}

  /**
   * Called when the application has been initialized. Invoked on the EDT.
   *
   * **NB:** the starter should take care of terminating the JVM when appropriate by calling [System.exit].
   *
   * @param args program arguments (including the command)
   */
  fun main(args: List<String>) {}

  /**
   * Applications that are incapable of working in a headless mode should override the method and return `false`.
   */
  val isHeadless: Boolean get() = true

  /**
   * Controls whether the platform may collect and report feature usage statistics while this starter runs.
   *
   * The flag is only consulted when the application runs in a headless environment. In non-headless mode
   * (when [isHeadless] returns `false`) statistics are always governed by the normal user-consent flow,
   * and overriding this property to `false` has no effect.
   *
   * In headless mode statistics are suppressed by default (the default value is `!isHeadless`), so that
   * runs on CI, in tests, and in other automated environments do not pollute the collected data.
   * Starters representing a user-facing product feature should override and return `true` to opt in.
   *
   * Opting in does not bypass user consent: the platform still checks it,
   * see `StatisticsUploadAssistant.isCollectAllowed`.
   *
   * Keep the default for starters used by CI jobs, tests or build tooling.
   */
  val shouldReportStatistics: Boolean get() = !isHeadless

  /**
   * Applications that are capable of processing command-line arguments within a running IDE instance
   * should return `true` from this method and implement [processExternalCommandLine].
   */
  fun canProcessExternalCommandLine(): Boolean = false

  /** @see [canProcessExternalCommandLine] */
  suspend fun processExternalCommandLine(args: List<String>, currentDirectory: String?): CliResult =
    throw UnsupportedOperationException("Class ${javaClass.name} must implement `processExternalCommandLineAsync()`")
}
