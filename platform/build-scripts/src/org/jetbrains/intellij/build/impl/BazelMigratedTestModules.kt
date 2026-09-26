// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Files
import java.nio.file.Path

/**
 * The modules whose tests run only under Bazel, read from [FILE_PATH]. The file documents its format.
 */
@Internal
class BazelMigratedTestModules private constructor(
  /** The patterns in file order. */
  val patterns: List<String>,
) {
  private val names: Set<String> = patterns.filter { !it.endsWith('*') }.toHashSet()
  private val prefixes: List<String> = patterns.filter { it.endsWith('*') }.map { it.dropLast(1) }

  operator fun contains(moduleName: String): Boolean = moduleName in names || prefixes.any { moduleName.startsWith(it) }

  companion object {
    /** The path of the file, relative to the community root. */
    const val FILE_PATH: String = "build/bazel-migrated-test-modules.txt"

    private val PATTERN_REGEX = Regex("[A-Za-z0-9._-]+\\*?")

    fun load(communityHome: Path): BazelMigratedTestModules {
      val file = communityHome.resolve(FILE_PATH)
      return parse(Files.readAllLines(file), source = file.toString())
    }

    /** Throws [IllegalArgumentException] on the first line that breaks the format. [source] names the file in the message. */
    fun parse(lines: List<String>, source: String = FILE_PATH): BazelMigratedTestModules {
      val patterns = ArrayList<String>()
      for ((index, line) in lines.withIndex()) {
        if (line.isEmpty() || line.startsWith('#')) {
          continue
        }
        require(PATTERN_REGEX.matches(line)) {
          "$source:${index + 1}: '$line' is not a module name or a module name prefix with '*' at the end. " +
          "A pattern contains only letters, digits, '.', '-' and '_', and a line has no whitespace around it."
        }
        require(line !in patterns) { "$source:${index + 1}: duplicate pattern '$line'" }
        patterns.add(line)
      }
      return BazelMigratedTestModules(patterns)
    }
  }
}
