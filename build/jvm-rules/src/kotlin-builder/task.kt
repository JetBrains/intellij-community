// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.kotlin

enum class RuleKind {
  LIBRARY,
  BINARY,
  TEST,
  IMPORT
}

enum class Platform {
  JVM, UNRECOGNIZED
}

data class CompilationTaskInfo(
  @JvmField val label: String,
  @JvmField val platform: Platform,
  @JvmField val ruleKind: RuleKind,
  @JvmField val moduleName: String,
)