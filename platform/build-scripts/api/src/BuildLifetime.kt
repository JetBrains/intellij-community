// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.platform.buildScripts.concurrency.SharedTaskOwner
import com.intellij.platform.buildScripts.concurrency.TaskFailedException
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.telemetry.TraceManager

/**
 * Owns shared tasks and resources for a build. Close it after all build scopes have closed.
 *
 * The constructor also installs the build-failure reporter on the current thread.
 */
@ApiStatus.Internal
class BuildLifetime(borrowedHttpSession: BuildHttpSession? = null) : AutoCloseable {
  val sharedTasks: SharedTaskOwner = SharedTaskOwner("build")

  init {
    installBuildFailureReporter()
  }

  private val httpSession: BuildHttpSession by lazy {
    borrowedHttpSession ?: BuildHttpSession().also { sharedTasks.onClose(it) }
  }

  /** Shares the build's connection pool without transferring its ownership to a caller. */
  val http: BuildHttpSession
    get() {
      sharedTasks.checkOpen()
      return httpSession
    }

  /** Closes every registered resource, the last one first. The first failure is thrown with the others suppressed. */
  override fun close() {
    sharedTasks.close()
  }
}

/**
 * Turns a failure that kills the current thread into a readable report.
 *
 * A build-target `main` creates the [BuildLifetime] on the main thread.
 * A failure unwinds `use`, closes the lifetime, and then leaves `main` uncaught.
 * `BuildScriptLauncher` catches the failure before it leaves `main`, so it delegates the failure to this handler itself.
 * The handler stops the span export first, so no span dump prints after the report.
 * Then it prints the stack trace once and prints a short cause-chain summary as the last output.
 * The JVM exits with code 1 because the failure left `main`.
 *
 * The handler stays installed after [BuildLifetime.close], because it runs after the close.
 * A test failure never reaches the handler, because the test framework catches it.
 */
private fun installBuildFailureReporter() {
  Thread.currentThread().setUncaughtExceptionHandler { _, failure ->
    runCatching { TraceManager.shutdown() }
    failure.printStackTrace()
    System.err.println(buildFailureSummary(failure))
  }
}

@ApiStatus.Internal
fun buildFailureSummary(failure: Throwable): String = buildString {
  val bar = "=".repeat(80)
  append('\n').append(bar).append('\n')
  append("BUILD FAILED").append('\n')
  var current: Throwable? = failure
  while (current != null) {
    val message = current.message
    val causeMessage = current.cause?.message
    // a wrapper such as TaskFailedException repeats the message of its cause; print each message once
    if (message == null || causeMessage == null || !message.endsWith(causeMessage)) {
      append("* ").append(summaryLine(current, message)).append('\n')
    }
    current = current.cause
  }
  append(bar)
}

private const val SUMMARY_LINE_LIMIT = 1000

private fun summaryLine(failure: Throwable, message: String?): String {
  val line = when {
    message == null -> failure.javaClass.name
    // the build wrappers have descriptive messages; other classes add context, e.g. a bare path in a JDK message
    failure is BuildScriptsLoggedError || failure is TaskFailedException -> message
    else -> "${failure.javaClass.simpleName}: $message"
  }
  return if (line.length <= SUMMARY_LINE_LIMIT) line else line.take(SUMMARY_LINE_LIMIT) + "… [truncated, see the stack trace above]"
}
