// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

internal fun StringBuilder.commaIfNeeded() {
  if (length > 2 && get(length - 1) == '\n' && get(length - 2) != '(' && get(length - 2) != ',') {
    setLength(length - 1)
    append(",\n")
  }
}

internal fun StringBuilder.line(string: String) {
  commaIfNeeded()
  appendLine("""  $string""")
}

internal inline fun StringBuilder.obj(name: String, writer: StringBuilder.() -> Unit) {
  append(name).append("(\n")
  writer()
  appendLine(")")
}

internal fun StringBuilder.array(name: String, list: List<String>) {
  commaIfNeeded()

  appendLine("""  $name = [""")
  appendLine("    " + list.joinToString(",\n    ") { "\"$it\"" })
  appendLine("  ]")
}