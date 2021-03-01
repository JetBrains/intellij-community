// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation.impl

import com.intellij.navigation.TargetPresentation
import com.intellij.navigation.TargetPresentationBuilder
import com.intellij.openapi.editor.markup.TextAttributes
import org.jetbrains.annotations.Nls
import java.awt.Color
import javax.swing.Icon

/**
 * `data` class for `#copy` method
 */
internal data class TargetPresentationBuilderImpl(
  override val backgroundColor: Color? = null,
  override val icon: Icon? = null,
  override val presentableText: @Nls String,
  override val presentableTextAttributes: TextAttributes? = null,
  override val containerText: @Nls String? = null,
  override val containerTextAttributes: TextAttributes? = null,
  override val locationText: @Nls String? = null,
  override val locationIcon: Icon? = null,
) : TargetPresentationBuilder, TargetPresentation {

  override fun presentation(): TargetPresentation = this

  override fun backgroundColor(color: Color?): TargetPresentationBuilder {
    return copy(backgroundColor = color)
  }

  override fun icon(icon: Icon?): TargetPresentationBuilder {
    return copy(icon = icon)
  }

  override fun presentableTextAttributes(attributes: TextAttributes?): TargetPresentationBuilder {
    return copy(presentableTextAttributes = attributes)
  }

  override fun containerText(text: String?): TargetPresentationBuilder {
    return copy(containerText = text)
  }

  override fun containerText(text: String?, attributes: TextAttributes?): TargetPresentationBuilder {
    return copy(containerText = text, containerTextAttributes = attributes)
  }

  override fun containerTextAttributes(attributes: TextAttributes?): TargetPresentationBuilder {
    return copy(containerTextAttributes = attributes)
  }

  override fun locationText(text: String?): TargetPresentationBuilder {
    return copy(locationText = text)
  }

  override fun locationText(text: String?, icon: Icon?): TargetPresentationBuilder {
    return copy(locationText = text, locationIcon = icon)
  }
}
