// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.worker.state

import java.nio.file.Path

interface PathRelativizer {
  fun toRelative(file: Path): String

  fun toAbsoluteFile(path: String): Path
}

enum class TargetConfigurationDigestProperty(@JvmField val description: String) {
  TOOL_JVM_VERSION("tool java runtime version"),
  KOTLIN_VERSION("kotlinc version"),
  COMPILER("kotlinc/javac configuration"),
  DEPENDENCY_PATH_LIST("dependency path list"),
  UNTRACKED_DEPENDENCY_DIGEST_LIST("untracked dependency digest list");
}

private fun emptyContainer(): LongArray {
  return LongArray(TargetConfigurationDigestProperty.entries.size)
}

@JvmInline
value class TargetConfigurationDigestContainer(
  private val list: LongArray = emptyContainer(),
) {
  fun get(kind: TargetConfigurationDigestProperty): Long = list[kind.ordinal]

  fun set(kind: TargetConfigurationDigestProperty, hash: Long) {
    list[kind.ordinal] = hash
  }
}