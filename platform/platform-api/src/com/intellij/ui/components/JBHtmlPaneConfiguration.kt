// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.util.ui.CSSFontResolver
import com.intellij.util.ui.ExtendableHTMLViewFactory
import org.jetbrains.annotations.ApiStatus.Experimental
import java.awt.Color
import java.awt.Image
import java.awt.event.ActionListener
import java.net.URL
import java.util.*
import javax.swing.Icon
import javax.swing.KeyStroke
import javax.swing.text.html.StyleSheet

@Experimental
class JBHtmlPaneConfiguration private constructor(builder: Builder) {
  val keyboardActions: Map<KeyStroke, ActionListener> = builder.keyboardActions
  val imageResolverFactory: (JBHtmlPane) -> Dictionary<URL, Image>? = builder.imageResolverFactory
  val iconResolver: (String) -> Icon? = builder.iconResolver
  val customStyleSheetProvider: (backgroundColor: Color) -> StyleSheet? = builder.customStyleSheetProvider
  val fontResolver: CSSFontResolver? = builder.fontResolver
  val extensions: List<ExtendableHTMLViewFactory.Extension> = builder.extensions

  constructor() : this(builder())

  constructor(configure: Builder.() -> Unit) : this(builder().also { configure(it) })

  class Builder {
    var keyboardActions: Map<KeyStroke, ActionListener> = emptyMap()
    var imageResolverFactory: (JBHtmlPane) -> Dictionary<URL, Image>? = { null }
    var iconResolver: (String) -> Icon? = { null }
    var customStyleSheetProvider: (backgroundColor: Color) -> StyleSheet? = { null }
    var fontResolver: CSSFontResolver? = null
    var extensions: List<ExtendableHTMLViewFactory.Extension> = emptyList()

    fun build(): JBHtmlPaneConfiguration = JBHtmlPaneConfiguration(this)

    fun keyboardActions(keyboardActions: Map<KeyStroke, ActionListener>): Builder =
      apply { this.keyboardActions = keyboardActions }

    fun imageResolverFactory(imageResolverFactory: (JBHtmlPane) -> Dictionary<URL, Image>?): Builder =
      apply { this.imageResolverFactory = imageResolverFactory }

    fun iconResolver(iconResolver: (String) -> Icon?): Builder =
      apply { this.iconResolver = iconResolver }

    fun customStyleSheetProvider(customStyleSheetProvider: (backgroundColor: Color) -> StyleSheet?): Builder =
      apply { this.customStyleSheetProvider = customStyleSheetProvider }

    fun fontResolver(fontResolver: CSSFontResolver?): Builder =
      apply { this.fontResolver = fontResolver }

    fun extensions(extensions: List<ExtendableHTMLViewFactory.Extension>): Builder =
      apply { this.extensions = extensions }

  }

  companion object {

    @JvmStatic
    fun builder(): Builder =
      Builder()

  }

}