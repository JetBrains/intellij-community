package com.intellij.tools.launch.os.terminal

enum class AnsiColor(val escapeSequence: String) {
  BLACK("\u001B[30m"),
  RED("\u001B[31m"),
  GREEN("\u001B[32m"),
  YELLOW("\u001B[33m"),
  BLUE("\u001B[34m"),
  PURPLE("\u001B[35m"),
  CYAN("\u001B[36m"),
  WHITE("\u001B[37m"),
}

fun colorize(text: String, color: AnsiColor): String {
  return "${color.escapeSequence}$text${Ansi.RESET}"
}

object Ansi {
  const val RESET = "\u001B[0m"
}