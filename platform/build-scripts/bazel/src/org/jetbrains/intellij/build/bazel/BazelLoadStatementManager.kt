// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

internal class BazelLoadStatementManager {

  private val comparator = BazelLabelComparator(forLoadStatements = true)
  private val loadStatements = mutableMapOf<String, List<String>>()

  fun insert(entries: Map<String, Set<String>>) {
    entries.entries.forEach { entry -> insert(entry.key, entry.value.toList().map { "\"$it\"" }) }
  }

  fun insert(line: String) {
    val matches = line.removePrefix("load(")
      .removeSuffix(")")
      .split(", ")
    val extension = matches.first()
      .removeSurrounding("\"")
    val symbols = matches.drop(1)
    insert(extension, symbols)
  }

  private fun insert(extension: String, symbols: List<String>) {
    loadStatements.compute(extension) { _, value ->
      (value.orEmpty() + symbols).sorted().distinct()
    }
  }

  fun getResult(): String {
    return loadStatements.entries
      .sortedWith { a, b -> comparator.compare(a.key, b.key) }
      .joinToString("\n") { entry ->
        """load("${entry.key}", ${entry.value.joinToString { it }})"""
      }
  }
}
