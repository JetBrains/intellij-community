// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler

import com.intellij.openapi.compiler.JavaCompilerBundle

enum class ParallelCompilationOption {
  ENABLED,
  AUTOMATIC,
  DISABLED;

  override fun toString(): String = when (this) {
    ENABLED -> JavaCompilerBundle.message("settings.compile.independent.modules.in.parallel.enabled")
    AUTOMATIC -> JavaCompilerBundle.message("settings.compile.independent.modules.in.parallel.automatic")
    DISABLED -> JavaCompilerBundle.message("settings.compile.independent.modules.in.parallel.disabled")
  }
}