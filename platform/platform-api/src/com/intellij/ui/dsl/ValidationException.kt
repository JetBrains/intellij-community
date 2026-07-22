// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl

import com.intellij.openapi.diagnostic.Logger

private val LOG = Logger.getInstance(ValidationException::class.java)

internal enum class ValidationError(internal val argsCount: Int) {
  STRING_TO_INT(1),
  INT_IN_RANGE(2),
}

internal class ValidationException(val error: ValidationError, vararg val args: Any = emptyArray()) : Exception() {
  init {
    checkTrue(error.argsCount == args.size)
  }

  override fun toString(): String {
    return "${javaClass.name}(error = $error, args = ${args.joinToString()})"
  }
}

internal fun catchValidationException(block: () -> Unit) {
  try {
    block.invoke()
  }
  catch (e: ValidationException) {
    LOG.debug(e.toString())
  }
}

@Throws(ValidationException::class)
internal fun stringToInt(string: String): Int {
  return string.toIntOrNull() ?: throw ValidationException(ValidationError.STRING_TO_INT, string)
}

@Throws(ValidationException::class)
internal fun validateIntInRange(value: Int, range: IntRange) {
  if (value !in range) {
    throw ValidationException(ValidationError.INT_IN_RANGE, value, range)
  }
}
