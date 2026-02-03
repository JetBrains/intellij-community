// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.buildtool.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import kotlin.io.path.Path
import kotlin.io.path.readLines

class Haven : CliktCommand(name = "haven") {
  override fun run(): Unit = Unit
}

fun main(args: Array<String>): Unit = Haven().subcommands(
  // TODO: add commands here
).main(parseArgs(args))

private fun parseArgs(startupArgs: Array<String>): List<String> {
  return when (startupArgs.size) {
    1 -> Path(startupArgs[0].removePrefixStrict("--flagfile=")).readLines()
    else -> startupArgs.toList()
  }
}

private fun String.removePrefixStrict(prefix: String): String {
  val result = removePrefix(prefix)
  check(result != this) {
    "String must start with $prefix but was: $this"
  }
  return result
}