// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative

data class HintFormat(
  val colorKind: HintColorKind,
  val fontSize: HintFontSize,
) {
  companion object {
    val default = HintFormat(
      HintColorKind.Default,
      HintFontSize.AsInEditor,
    )
  }

  fun withColorKind(newColorKind: HintColorKind) = copy(colorKind = newColorKind)
  fun withFontSize(newFontSize: HintFontSize) = copy(fontSize = newFontSize)
}

/**
 * Editor | Color Scheme | Language Defaults | Inline hints
 */
enum class HintColorKind {
  Default, Parameter, TextWithoutBackground;

  fun hasBackground() = this != TextWithoutBackground
}

enum class HintFontSize {
  AsInEditor,
  ABitSmallerThanInEditor
}
