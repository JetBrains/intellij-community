// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.stats

/**
 * ANSI color codes for terminal output.
 */
internal object AnsiColors {
  const val RESET = "\u001B[0m"
  const val BOLD = "\u001B[1m"
  const val RED = "\u001B[31m"
  const val GREEN = "\u001B[32m"
  const val YELLOW = "\u001B[33m"
  const val BLUE = "\u001B[34m"
  const val CYAN = "\u001B[36m"
  const val GRAY = "\u001B[90m"
}

/**
 * ANSI-aware style helper that returns color codes or empty strings based on [useAnsi] flag.
 */
class AnsiStyle(private val useAnsi: Boolean) {
  val reset: String get() = if (useAnsi) AnsiColors.RESET else ""
  val bold: String get() = if (useAnsi) AnsiColors.BOLD else ""
  val red: String get() = if (useAnsi) AnsiColors.RED else ""
  val yellow: String get() = if (useAnsi) AnsiColors.YELLOW else ""
  val blue: String get() = if (useAnsi) AnsiColors.BLUE else ""
  val gray: String get() = if (useAnsi) AnsiColors.GRAY else ""
}
