// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devServer

import java.nio.file.Path

internal class CommandLineOptions(private val values: Map<String, List<String>>) {
  private val used = HashSet<String>()

  fun optional(name: String): String? {
    used.add(name)
    val value = values.get(name) ?: return null
    require(value.size == 1) { "$name must be specified at most once, but got ${value.size} values: $value" }
    return value.single().takeIf { it.isNotEmpty() }
  }

  fun list(name: String): List<String> {
    used.add(name)
    return values.get(name)?.filter { it.isNotEmpty() } ?: emptyList()
  }

  fun optionalBoolean(name: String): Boolean? {
    val value = optional(name) ?: return null
    return when (value.lowercase()) {
      "true" -> true
      "false" -> false
      else -> error("$name must be 'true' or 'false', but got '$value'")
    }
  }

  fun optionalPath(name: String): Path? = optional(name)?.let(::toAbsolutePath)

  fun pathList(name: String): List<Path> = list(name).map(::toAbsolutePath)

  fun requiredPath(name: String, fallback: () -> String? = { null }): Path {
    val value = optional(name) ?: fallback() ?: error("$name is required (no value and no fallback available)")
    return toAbsolutePath(value)
  }

  fun checkNoUnknownOptions() {
    val unknown = values.keys - used
    check(unknown.isEmpty()) { "Unknown options: ${unknown.sorted().joinToString()}" }
  }

  private fun toAbsolutePath(value: String): Path = Path.of(value).toAbsolutePath().normalize()
}

internal fun parseCommandLineOptions(args: Array<String>): CommandLineOptions {
  val values = LinkedHashMap<String, MutableList<String>>()
  for (arg in args) {
    require(arg.startsWith("--")) { "Expected an option in the '--key=value' form, but got '$arg'" }
    val separatorIndex = arg.indexOf('=')
    val name = if (separatorIndex == -1) arg else arg.substring(0, separatorIndex)
    val value = if (separatorIndex == -1) "true" else arg.substring(separatorIndex + 1)
    values.computeIfAbsent(name) { ArrayList() }.add(value)
  }
  return CommandLineOptions(values)
}
