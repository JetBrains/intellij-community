package com.intellij.codeowners.model

import org.eclipse.jgit.ignore.FastIgnoreRule
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

class OwnershipMapping(
  val entries: List<OwnershipMappingEntry>,
) {
  init {
    entries.requireSorted { it.depth }
    entries.groupBy { it.depth }.forEach { (_, entries) ->
      entries.requireSorted { it.sourceFile }
    }
  }

  fun getMatch(filePath: Path): OwnershipMappingEntry? {
    require(!filePath.isAbsolute) { "File path must be non-absolute: $filePath" }
    // reversed as top-level rules go first so the last rule wins
    return firstMatch { it.matches(filePath) }
  }

  fun getMatch(filePath: String): OwnershipMappingEntry? {
    // reversed as top-level rules go first so the last rule wins
    return firstMatch { it.matches(filePath) }
  }

  private fun firstMatch(predicate: (OwnershipMappingEntry) -> Boolean): OwnershipMappingEntry? = entries.asReversed().firstOrNull(predicate)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as OwnershipMapping
    return entries == other.entries
  }

  override fun hashCode(): Int {
    return entries.hashCode()
  }

  override fun toString(): String {
    return "OwnershipMapping(entries.size=${entries.size})"
  }
}


data class OwnershipMappingEntry(
  val sourceFile: String,
  val rule: String,
  val owner: GroupName,
) {
  val depth: Int = sourceFile.count { it == '/' }

  private val jGitRule = FastIgnoreRule(rule)

  fun matches(filePath: String, isDirectory: Boolean = false): Boolean = jGitRule.isMatch(filePath, isDirectory)

  fun matches(filePath: Path): Boolean {
    require(!filePath.isAbsolute) { "File path must be non-absolute: $filePath" }
    return matches(filePath.invariantSeparatorsPathString)
  }
}

/**
 * Ensures this list is sorted in non-decreasing order by [keySelector].
 *
 * @throws IllegalArgumentException if the order is violated
 */
private fun <T, R : Comparable<R>> List<T>.requireSorted(keySelector: (T) -> R) {
  if (size <= 1) return

  var prevKey = keySelector(this[0])

  for (i in 1 until size) {
    val currKey = keySelector(this[i])
    require(currKey >= prevKey) {
      "List is not sorted at index $i: prevKey=$prevKey (index ${i - 1}), currKey=$currKey (index $i)"
    }
    prevKey = currKey
  }
}

