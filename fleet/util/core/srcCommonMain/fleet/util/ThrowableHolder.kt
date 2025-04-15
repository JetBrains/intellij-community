// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import kotlinx.serialization.Serializable

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
  val exceptionType: String, //under JVM, it's the exception class name
  val stackTrace: List<String>?, //each string is a single line of text, trimmed.
  // Is not supposed to contain trailing offset chars and "at" word
  val cause: ThrowableHolder?,
  val suppressed: List<ThrowableHolder>?,
) {
  fun getMessageString(): String = "$exceptionType: $message"

  fun getStacktraceString(): String = StringBuilder().also { appendStacktraceString(it) }.toString()

  private fun appendStacktraceString(sb: StringBuilder, additionalOffset: Int = 0) {
    //todo suppressed
    val offset = "".repeat(additionalOffset)
    stackTrace?.forEach { sb.append(offset).append("  at ").append(it).append('\n') }

    if (suppressed != null) {
      for (se in suppressed) {
        sb.append(offset).append("  Suppressed: ${se.getMessageString()}\n")
        se.appendStacktraceString(sb, additionalOffset + 2)
      }
    }

    if (cause != null) {
      sb.append(offset).append("Caused by: ${cause.getMessageString()}\n")
      cause.appendStacktraceString(sb, additionalOffset)
    }
  }
}

