// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import kotlinx.serialization.Serializable

@Serializable
data class StackTraceElementHolder(
  val classLoaderName: String?,
  val moduleName: String?,
  val moduleVersion: String?,
  val declaringClass: String?,
  val methodName: String?,
  val fileName: String?,
  val lineNumber: Int
)

fun StackTraceElementHolder.toSTE() =
  StackTraceElement(classLoaderName, moduleName, moduleVersion, declaringClass, methodName, fileName, lineNumber)

fun StackTraceElement.toSTEH() =
  StackTraceElementHolder(classLoaderName, moduleName, moduleVersion, className, methodName, fileName, lineNumber)

@Serializable
data class LogErrorHolder(
  val logMessage: String,
  val throwable: ThrowableHolder,
)

/**
 * Stores exception data.
 *
 * Required not to have memleaks when storing exception classes from plugins. Instead, freeze exception data and store it as this class.
 */
@Serializable
data class ThrowableHolder(
  val message: String?,
  val className: String,
  val stackTraceElementProxies: List<StackTraceElementHolder>?,
  val cause: ThrowableHolder?,
  val suppressed: List<ThrowableHolder>
) {
  constructor(e: Throwable)
    : this(e.localizedMessage ?: e.message,
           e.javaClass.name,
           e.stackTrace.map { it.toSTEH() },
           e.cause?.let { ThrowableHolder(it) },
           e.suppressedExceptions.map { ThrowableHolder(it) })

  fun getMessageString() = "$className: $message"

  fun getStacktraceString(offset: Int = 2): String {
    val sb: StringBuilder = StringBuilder()
    appendStacktraceString(sb, offset)
    return sb.toString()
  }

  private fun appendStacktraceString(sb: StringBuilder, offset: Int) {
    //todo match kotlin exception formatting, suppressed
    stackTraceElementProxies?.forEach {
      sb
        .append(" ".repeat(offset))
        .append("at ${it.toSTE()}\n")
    }
    if (cause != null) {
      sb.append(" ".repeat(offset))
        .append("Caused by: ${cause.getMessageString()}")
      cause.appendStacktraceString(sb, offset)
    }
  }
}

