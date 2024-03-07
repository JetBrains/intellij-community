// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.util.ui.CSSFontResolver
import com.intellij.util.ui.ExtendableHTMLViewFactory
import java.awt.Color
import java.awt.Image
import java.awt.event.ActionListener
import java.net.URL
import java.util.*
import javax.swing.Icon
import javax.swing.KeyStroke
import javax.swing.text.html.StyleSheet

data class JBHtmlPaneConfiguration(
  val keyboardActions: Map<KeyStroke, ActionListener> = emptyMap(),
  val imageResolverFactory: (JBHtmlPane) -> Dictionary<URL, Image>? = { null },
  val iconResolver: (String) -> Icon? = { null },
  val customStyleSheetProvider: (backgroundColor: Color) -> StyleSheet? = { null },
  val fontResolver: CSSFontResolver? = null,
  val extensions: List<ExtendableHTMLViewFactory.Extension> = emptyList()
)