// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.util

internal data class Arg(val option: String?, val value: String?) {
  init {
    requireNotNull(option ?: value)
  }
}

internal fun parseArgs(args: Array<String>): Sequence<Arg> {
  return sequence {
    var skip = false
    var positional = false
    for ((i, arg) in args.withIndex()) {
      if (skip) {
        skip = false
        continue
      }
      if (!positional && arg == "--") {
        positional = true
        continue
      }

      val option = arg.takeIf { it.startsWith("--") }?.let {
        if ("=" in it) it.substringBefore("=") else it
      }

      val value = when {
        positional || option == null -> arg
        "=" in arg -> {
          arg.substringAfter("=")
        }
        else -> {
          skip = true
          args.getOrNull(i + 1)?.takeUnless { it.startsWith("--") }
        }
      }

      yield(Arg(option, value))
    }
  }
}
