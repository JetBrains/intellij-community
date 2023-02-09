// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.vmOptions

internal object VMOptionsParser {
  internal fun parseXXOptions(text: String) : List<VMOption> {
    val lines = text.lineSequence().drop(1)
    val options = lines.mapNotNull {
      val lbraceIndex = it.indexOf("{")
      if (lbraceIndex == -1) return@mapNotNull null
      val rbraceIndex = it.indexOf("}")
      if (rbraceIndex == -1) return@mapNotNull null
      val kind = it.substring(lbraceIndex, rbraceIndex)
      val optionKind = if (kind.contains("product")) {
        VMOptionKind.Product
      }
      else if (kind.contains("experimental")) {
        VMOptionKind.Experimental
      }
      else if (kind.contains("diagnostic")) {
        VMOptionKind.Diagnostic
      }
      else {
        return@mapNotNull null
      }
      val fragments = it.split(" ").filter { part -> part.isNotBlank() }
      if (fragments.isEmpty()) return@mapNotNull null
      val indexOfEq = fragments.indexOf("=")
      val maybeDefault = fragments.getOrNull(indexOfEq + 1) ?: return@mapNotNull null
      val default  = if (maybeDefault.startsWith("{")) {
        null
      } else {
        maybeDefault
      }
      VMOption(fragments[1], fragments[0], default, optionKind, null, VMOptionVariant.XX)
    }.toList()

    return options
  }

  internal fun parseXOptions(stderr: String): List<VMOption>? {
    val options = ArrayList<VMOption>()
    var currentOption: OptionBuilder? = null
    var tailIndex = stderr.indexOf("These extra options are subject to change without notice.")
    if (tailIndex == -1) {
      tailIndex = stderr.indexOf("The -X options are non-standard and subject to change without notice.")
    }
    if (tailIndex == -1) return null
    val separators = charArrayOf(' ', '<')
    for (line in stderr.substring(0, tailIndex).trimStart().lines()) {
      val trimmed = line.trim()
      val variant = when {
        trimmed.startsWith("-X") -> VMOptionVariant.X
        trimmed.startsWith("--") -> VMOptionVariant.DASH_DASH
        else -> null
      }
      if (variant != null) {
        if (currentOption != null) {
          options.add(currentOption.build())
        }
        val indexOfSeparator = trimmed.indexOfAny(separators)
        if (indexOfSeparator != -1) {
          currentOption = OptionBuilder(variant, trimmed.substring(2, indexOfSeparator))
          currentOption.doc.add(trimmed.substring(indexOfSeparator).trim())
        }
        else {
          currentOption = OptionBuilder(variant, trimmed.substring(2))
        }
      }
      else {
        currentOption?.doc?.add(trimmed)
      }
    }
    if (currentOption != null) {
      options.add(currentOption.build())
    }

    return options
  }

  private class OptionBuilder(val variant: VMOptionVariant, name: String) {
    val name = name.split("<")[0]
    val doc = ArrayList<String>()


    fun build(): VMOption {
      return VMOption(name, type = null, defaultValue = null, kind = VMOptionKind.Product, doc.joinToString(separator = " ") { it }, variant)
    }
  }
}