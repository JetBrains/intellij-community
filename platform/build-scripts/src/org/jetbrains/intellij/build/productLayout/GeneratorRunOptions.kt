// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout

import java.nio.file.Path

/**
 * Options controlling generator execution.
 * Encapsulates CLI arguments and generation behavior flags.
 *
 * @param jsonFilter If non-null, outputs JSON analysis instead of generating files (raw CLI arg like `--json` or `--json={...}`)
 * @param commitChanges If true, `commit` writes to disk when validation passes
 * @param updateSuppressions If true, updates suppressions.json (no XML changes)
 * @param validationFilter If non-null, only runs validation rules with matching names
 * @param logFilter If non-null, enables debug output. Empty set = all debug, non-empty = only matching tags.
 * @param devSectionsDumpDir If non-null, the run writes the rendered dev-distribution build sections into this directory and generates nothing else
 */
data class GeneratorRunOptions(
  @JvmField val jsonFilter: String? = null,
  @JvmField val commitChanges: Boolean = true,
  @JvmField val updateSuppressions: Boolean = false,
  @JvmField val validationFilter: Set<String>? = null,
  @JvmField val logFilter: Set<String>? = null,
  @JvmField val devSectionsDumpDir: Path? = null,
)

/**
 * Parses `--dump-dev-sections=<dir>`.
 * Returns null when the argument is absent.
 */
private fun parseDevSectionsDumpDir(args: Array<String>): Path? {
  val arg = args.firstOrNull { it.startsWith("--dump-dev-sections=") } ?: return null
  val value = arg.substringAfter("=")
  require(value.isNotEmpty()) { "--dump-dev-sections needs a directory" }
  return Path.of(value)
}

/**
 * Parses `--validation=<ids>` argument.
 * - `--validation=none` returns empty set (skip all validation)
 * - `--validation=id1,id2` returns set of specified IDs
 * - Not specified returns null (run all validation)
 */
private fun parseValidationFilter(args: Array<String>): Set<String>? {
  val arg = args.firstOrNull { it.startsWith("--validation=") } ?: return null
  val value = arg.substringAfter("=")
  if (value == "none") return emptySet()
  return value.splitToSequence(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}

/**
 * Parses `--log` argument.
 * - `--log` or `--log=*` returns empty set (all debug output)
 * - `--log=tag1,tag2` returns set of specified tags
 * - Not specified returns null (no debug output)
 */
private fun parseLogFilter(args: Array<String>): Set<String>? {
  val arg = args.firstOrNull { it.startsWith("--log") } ?: return null
  return when {
    arg == "--log" -> emptySet()
    arg.contains('=') -> {
      val value = arg.substringAfter("=")
      if (value == "*" || value == "true") emptySet()
      else value.splitToSequence(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }
    else -> emptySet()
  }
}

/**
 * Parses command line arguments into [GeneratorRunOptions].
 */
internal fun parseGeneratorOptions(args: Array<String>): GeneratorRunOptions {
  val jsonArg = args.firstOrNull { it.startsWith("--json") }
  val updateSuppressions = args.any { it == "--update-suppressions" }
  // Report what would change and write nothing. The generate run rewrites whatever is out of sync anywhere in the
  // repository, which is not what you want when you only meant to see one artifact's diff.
  val check = args.any { it == "--check" }
  val validationFilter = parseValidationFilter(args)
  val logFilter = parseLogFilter(args)
  // A dump run renders the sections into a directory of its own and writes nothing into the repository.
  val devSectionsDumpDir = parseDevSectionsDumpDir(args)

  return GeneratorRunOptions(
    jsonFilter = jsonArg,
    commitChanges = jsonArg == null && !updateSuppressions && !check && devSectionsDumpDir == null,
    updateSuppressions = updateSuppressions,
    validationFilter = validationFilter,
    logFilter = logFilter,
    devSectionsDumpDir = devSectionsDumpDir,
  )
}