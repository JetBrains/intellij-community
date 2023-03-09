// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.vmOptions


import com.intellij.util.lang.JavaVersion

data class VMOption(
  val optionName: String,
  val type: String?,
  val defaultValue: String?,
  val kind: VMOptionKind,
  val doc: String?,
  val variant: VMOptionVariant
)

enum class VMOptionVariant {
  XX,
  X,
}

enum class VMOptionKind {
  Product,
  Diagnostic,
  Experimental
}

data class VMOptionsInfo(
  val options: List<VMOption>,
  val vmInfo: VMInfo
)

data class VMInfo(
  val version: JavaVersion,
  val vendor: String
)