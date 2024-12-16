/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.jetbrains.bazel.jvm.kotlin

/**
 * CompilationArgs collects the arguments for executing the Kotlin compiler.
 */
class CompilationArgs(
  val args: MutableList<String> = mutableListOf(),
) {
  interface SetFlag {
    fun flag(
      name: String,
      value: String,
    ): SetFlag
  }

  //fun plugin(p: CompilerPlugin): CompilationArgs = plugin(p) {}

  fun plugin(
    id: String,
    flagArgs: SetFlag.() -> Unit,
  ) {
    object : SetFlag {
      override fun flag(
        name: String,
        value: String,
      ): SetFlag {
        args.add("-P")
        args.add("plugin:$id:$name=$value")
        return this
      }
    }.flagArgs()
  }

  operator fun plus(other: CompilationArgs): CompilationArgs {
    return CompilationArgs((args.asSequence() + other.args.asSequence()).toMutableList())
  }

  fun value(value: String): CompilationArgs {
    args.add(value)
    return this
  }

  fun append(compilationArgs: CompilationArgs): CompilationArgs {
    args.addAll(compilationArgs.args)
    return this
  }

  fun flag(value: String): CompilationArgs {
    args.add(value)
    return this
  }

  fun flag(
    key: String,
    value: () -> String,
  ): CompilationArgs {
    args.add(key)
    args.add(value())
    return this
  }

  fun flag(
    flag: String,
    value: String,
  ): CompilationArgs {
    args.add(flag)
    args.add(value)
    return this
  }

  fun values(values: Collection<String>): CompilationArgs {
    args.addAll(values)
    return this
  }

  fun xFlag(
    flag: String,
    value: String,
  ): CompilationArgs {
    args.add("-X$flag=$value")
    return this
  }

  fun toList(): List<String> = args.toList()
}
