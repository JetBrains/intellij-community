// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.presentation

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Graphics2D

class WithAttributesPresentation(presentation: InlayPresentation,
                                 val textAttributesKey: TextAttributesKey,
                                 editor: Editor,
                                 val flags: AttributesFlags = AttributesFlags()
) : StaticDelegatePresentation(presentation) {
  private val colorsScheme = editor.colorsScheme

  override fun paint(g: Graphics2D, attributes: TextAttributes) {
    val other = colorsScheme.getAttributes(textAttributesKey) ?: TextAttributes()
    if (flags.skipEffects) {
      other.effectType = null
    }
    if (flags.skipBackground) {
      other.backgroundColor = null
    }
    if (!flags.isDefault) {
      super.paint(g, other)
    }
    else {
      val result = attributes.clone()
      if (result.foregroundColor == null) {
        result.foregroundColor = other.foregroundColor
      }
      if (result.backgroundColor == null) {
        result.backgroundColor = other.backgroundColor
      }
      if (result.effectType == null) {
        result.effectType = other.effectType
      }
      if (result.effectColor == null) {
        result.effectColor = other.effectColor
      }
      super.paint(g, result)
    }
  }

  class AttributesFlags {
    var skipEffects: Boolean = false
    var skipBackground: Boolean = false
    var isDefault: Boolean = false

    fun withSkipEffects(skipEffects: Boolean): AttributesFlags {
      this.skipEffects = skipEffects
      return this
    }

    fun withSkipBackground(skipBackground: Boolean): AttributesFlags {
      this.skipBackground = skipBackground
      return this
    }

    fun withIsDefault(isDefault: Boolean): AttributesFlags {
      this.isDefault = isDefault
      return this
    }
  }
}