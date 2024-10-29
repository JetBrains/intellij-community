// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex

enum class CodeQualityCategories(val id: String) {
  SECURITY("Security"),
  PERFORMANCE("Performance"),
  LEGAL("Legal"),
  CODE_STYLE("Code Style"),
  RELIABILITY("Reliability"),
  SANITY("Sanity"),
  UNSPECIFIED("Unspecified")
}