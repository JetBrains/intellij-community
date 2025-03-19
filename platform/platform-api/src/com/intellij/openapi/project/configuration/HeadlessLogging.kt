// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.configuration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.configuration.HeadlessLogging.Message.Plain
import kotlinx.coroutines.flow.SharedFlow

/**
 * A sink for information that may be useful during command-line execution of the IDE.
 *
 * Each subsystem can use the methods in this class to send some information that may be of value to the end user.
 *
 * We do not provide a distinction between the severity levels here, as all the reported messages are of interest for the user.
 * Severity differentiation makes sense for investigation of internal problems, and for this reason we have internal logging.
 */
object HeadlessLogging {

  /**
   * Reports an informational message about the IDE.
   *
   * This kind of messages is important to keep track of progress and to assure the user that the execution is not stuck.
   */
  fun logMessage(message: String) = HeadlessLoggingService.getInstance().logEntry(LogEntry(SeverityKind.Info, Plain(message)))

  /**
   * Reports a warning message.
   *
   * The collector of the message can decide to react differently to warnings, i.e., print them in different colors in the console,
   * or collect and report them batched at the end of the configuration.
   */
  fun logWarning(message: String) = HeadlessLoggingService.getInstance().logEntry(LogEntry(SeverityKind.Warning, Plain(message)))

  /**
   * Reports a non-fatal exception. An exception is non-fatal if the IDE can recover from it.
   *
   * This kind of error does not stop the execution of the IDE.
   */
  fun logWarning(exception: Throwable) = HeadlessLoggingService.getInstance().logEntry(LogEntry(SeverityKind.Warning, Message.Exception(exception)))

  /**
   * Reports a fatal error during the execution. An error is fatal if the user's actions are required in order to fix the problem.
   *
   * This kind of errors **has influence on control flow**: once the IDE reports a fatal error,
   * the headless execution may stop. An example of this error is a failure in the build system import.
   * This
   */
  fun logFatalError(exception: Throwable) = HeadlessLoggingService.getInstance().logEntry(LogEntry(SeverityKind.Fatal, Message.Exception(exception)))
  fun logFatalError(message: String) = HeadlessLoggingService.getInstance().logEntry(LogEntry(SeverityKind.Fatal, Plain(message)))

  /**
   * Retrieves a hot flow with messages about the IDE.
   */
  fun loggingFlow(): SharedFlow<LogEntry> = HeadlessLoggingService.getInstance().loggingFlow()

  enum class SeverityKind {
    Info,
    Warning,
    Fatal
  }

  sealed interface Message {
    fun representation(): String

    @JvmInline
    value class Plain(val message: String) : Message {
      override fun representation(): String = message
    }

    @JvmInline
    value class Exception(val exception: Throwable) : Message {
      override fun representation(): String = exception.toString()
    }
  }

  data class LogEntry(val severity: SeverityKind, val message: Message)

  interface HeadlessLoggingService {
    companion object {
      fun getInstance(): HeadlessLoggingService {
        return ApplicationManager.getApplication().getService(HeadlessLoggingService::class.java)
      }
    }

    fun logEntry(logEntry: LogEntry)
    fun loggingFlow(): SharedFlow<LogEntry>
  }
}