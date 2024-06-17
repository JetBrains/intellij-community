// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative

/**
 * Editor | Color Scheme | Language Defaults | Inline hints
 */
enum class HintColorKind {
  Default, Parameter, TextWithoutBackground;

  fun hasBackground() = this != TextWithoutBackground
}