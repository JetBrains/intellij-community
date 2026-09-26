// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative

data class HintFormat(
  val colorKind: HintColorKind,
  val fontSize: HintFontSize,
  val horizontalMarginPadding: HintMarginPadding,
) {
  companion object {
    val default = HintFormat(
      HintColorKind.Default,
      HintFontSize.AsInEditor,
      HintMarginPadding.OnlyPadding,
    )
  }

  fun withColorKind(newColorKind: HintColorKind) = copy(colorKind = newColorKind)
  fun withFontSize(newFontSize: HintFontSize) = copy(fontSize = newFontSize)
  fun withHorizontalMargin(newHorizontalMarginPadding: HintMarginPadding) = copy(horizontalMarginPadding = newHorizontalMarginPadding)
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

enum class HintMarginPadding {
  OnlyPadding,
  MarginAndSmallerPadding,
}
