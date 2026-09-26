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

  /**
   * Removes each symbol that [isUsed] rejects, and removes an extension that keeps no symbol.
   *
   * [isUsed] receives the name the load statement binds. For `alias = "original"` that name is `alias`.
   */
  fun retainUsedSymbols(isUsed: (String) -> Boolean) {
    val iterator = loadStatements.entries.iterator()
    while (iterator.hasNext()) {
      val entry = iterator.next()
      val kept = entry.value.filter { isUsed(boundName(it)) }
      if (kept.isEmpty()) {
        iterator.remove()
      }
      else if (kept.size != entry.value.size) {
        entry.setValue(kept)
      }
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

/**
 * The name a load statement entry binds in the BUILD file.
 *
 * An entry is either `"symbol"` or `alias = "symbol"`.
 */
private fun boundName(entry: String): String {
  val alias = entry.substringBefore(delimiter = '=', missingDelimiterValue = "").trim()
  return alias.ifEmpty { entry.trim().removeSurrounding("\"") }
}
