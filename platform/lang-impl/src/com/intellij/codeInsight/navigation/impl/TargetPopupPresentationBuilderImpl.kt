// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation.impl

import com.intellij.navigation.TargetPopupPresentation
import com.intellij.navigation.TargetPopupPresentationBuilder
import com.intellij.openapi.editor.markup.TextAttributes
import org.jetbrains.annotations.Nls
import java.awt.Color
import javax.swing.Icon

/**
 * `data` class for `#copy` method
 */
internal data class TargetPopupPresentationBuilderImpl(
  override val backgroundColor: Color? = null,
  override val icon: Icon? = null,
  override val presentableText: @Nls String,
  override val presentableTextAttributes: TextAttributes? = null,
  override val containerText: @Nls String? = null,
  override val containerTextAttributes: TextAttributes? = null,
  override val locationText: @Nls String? = null,
  override val locationIcon: Icon? = null,
) : TargetPopupPresentationBuilder, TargetPopupPresentation {

  override fun presentation(): TargetPopupPresentation = this

  override fun backgroundColor(color: Color?): TargetPopupPresentationBuilder {
    return copy(backgroundColor = color)
  }

  override fun icon(icon: Icon?): TargetPopupPresentationBuilder {
    return copy(icon = icon)
  }

  override fun presentableTextAttributes(attributes: TextAttributes?): TargetPopupPresentationBuilder {
    return copy(presentableTextAttributes = attributes)
  }

  override fun containerText(text: String?): TargetPopupPresentationBuilder {
    return copy(containerText = text)
  }

  override fun containerText(text: String?, attributes: TextAttributes?): TargetPopupPresentationBuilder {
    return copy(containerText = text, containerTextAttributes = attributes)
  }

  override fun containerTextAttributes(attributes: TextAttributes?): TargetPopupPresentationBuilder {
    return copy(containerTextAttributes = attributes)
  }

  override fun locationText(text: String?): TargetPopupPresentationBuilder {
    return copy(locationText = text)
  }

  override fun locationText(text: String?, icon: Icon?): TargetPopupPresentationBuilder {
    return copy(locationText = text, locationIcon = icon)
  }
}
