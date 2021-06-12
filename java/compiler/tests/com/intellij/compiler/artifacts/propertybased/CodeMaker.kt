// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.artifacts.propertybased

/**
 * This is an experimental thing to generate source code for failed property test.
 */
class CodeMaker {
  private val code = ArrayList<String>()
  private val variables: MutableMap<String, Int> = HashMap()
  private var indent = 0
  private var collectCode = true

  fun finish() {
    while (indent > 0) {
      finishScope()
    }
    collectCode = false
  }

  fun scope(scopeName: String, action: () -> Unit) {
    startScope(scopeName)
    try {
      action()
    }
    finally {
      finishScope()
    }
  }

  fun startScope(scopeName: String) {
    addLine("$scopeName {")
    indent += 1
  }

  fun startScope(withVar: String, scopeName: String): String {
    val varName = uniqueVarName(withVar)
    addLine("val $varName = $scopeName {")
    indent += 1
    return varName
  }

  fun last(): String? = code.lastOrNull()

  fun finishScope() {
    indent -= 1
    addLine("}")
  }

  // last created variable by base name
  fun v(base: String): String {
    val counter = variables.getValue(base)
    return makeVarName(base, counter)
  }

  // last created variable by base name
  fun vOrNull(base: String): String? {
    val counter = variables[base] ?: return null
    return makeVarName(base, counter)
  }

  fun addLine(line: String) {
    if (!collectCode) return
    code.add("  ".repeat(indent) + line)
  }

  fun makeVal(baseName: String, value: String?): String {
    val varName = uniqueVarName(baseName)
    addLine("val $varName = $value")
    return varName
  }

  private fun uniqueVarName(baseName: String): String {
    val counter = variables.getOrPut(baseName) { 0 } + 1
    variables[baseName] = counter
    val varName = makeVarName(baseName, counter)
    return varName
  }

  private fun makeVarName(baseName: String, counter: Int) = if (counter == 1) baseName else baseName + "_" + counter

  fun get(): String {
    return code.joinToString("\n")
  }
}