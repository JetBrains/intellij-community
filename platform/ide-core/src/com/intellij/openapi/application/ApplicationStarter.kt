// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.ide.CliResult
import com.intellij.openapi.extensions.ExtensionPointName
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@Experimental
abstract class ModernApplicationStarter : ApplicationStarter {
  final override val requiredModality: Int
    get() = ApplicationStarter.NOT_IN_EDT

  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated(message = "use start", level = DeprecationLevel.ERROR)
  final override fun main(args: List<String>): Unit =
    throw UnsupportedOperationException("Use start(args)")

  abstract suspend fun start(args: List<String>)
}

/**
 * This extension point allows running a custom [command-line] application based on the IntelliJ platform.
 *
 * A command may come directly (there were no other instances of the application running, so the command starts a new one),
 * or externally (the application has detected a running instance and passed a command to it). In the former case, the platform
 * invokes [.premain] and [.main] methods, in the latter - [.processExternalCommandLineAsync].
 */
interface ApplicationStarter {
  companion object {
    val EP_NAME = ExtensionPointName<ApplicationStarter>("com.intellij.appStarter")

    const val NON_MODAL = 1
    const val ANY_MODALITY = 2
    const val NOT_IN_EDT = 3
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
  val requiredModality: Int
    get() = NON_MODAL

  /**
   * Command-line switch to start with this runner.
   * For example, return `"inspect"` if you'd like to start an app with `"idea.exe inspect ..."` command.
   */
  @Deprecated("Specify it as `id` for extension definition in a plugin descriptor")
  val commandName: String?

  /**
   * Called before application initialization.
   *
   * @param args program arguments (including the command)
   */
  fun premain(args: List<String>) = Unit

  /**
   *
   * Called when application has been initialized. Invoked in event dispatch thread.
   *
   * An application starter should take care of terminating JVM when appropriate by calling [System.exit].
   *
   * @param args program arguments (including the selector)
   */
  fun main(args: List<String>) = Unit

  /**
   * Applications that are incapable of working in a headless mode should override the method and return `false`.
   */
  val isHeadless: Boolean
    get() = true

  /**
   * Applications that are capable of processing command-line arguments within a running IDE instance
   * should return `true` from this method and implement [.processExternalCommandLineAsync].
   *
   * @see .processExternalCommandLineAsync
   */
  fun canProcessExternalCommandLine(): Boolean = false

  /** @see .canProcessExternalCommandLine */
  suspend fun processExternalCommandLine(args: List<String>, currentDirectory: String?): CliResult =
    throw UnsupportedOperationException("Class ${javaClass.name} must implement `processExternalCommandLineAsync()`")
}
