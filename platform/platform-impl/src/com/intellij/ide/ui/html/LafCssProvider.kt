// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("CssInvalidPropertyValue", "CssUnknownProperty", "CssUnusedSymbol")

package com.intellij.ide.ui.html

import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale

private val PX_SIZE_REGEX = Regex("""(\d+)px""")
private val LAF_COLOR_REGEX = Regex("""LAF_COLOR\((.+)\)""")

@Suppress("CssInvalidHtmlTagReference")
private const val EDITOR_FONT_FAMILY_STUB = """___EDITOR_FONT___"""
@Suppress("CssInvalidHtmlTagReference")
private const val EDITOR_FOREGROUND_STUB = """___EDITOR_FOREGROUND___"""
@Suppress("CssInvalidHtmlTagReference")
private const val EDITOR_BACKGROUND_STUB = """___EDITOR_BACKGROUND___"""

@Service(Service.Level.APP)
internal class LafCssProvider {
  /**
   * Get custom css styles and overrides for default swing CSS
   */
  fun getCssForCurrentLaf(): String {
    return (defaultOverridesCss + customCss)
      .replace(PX_SIZE_REGEX) {
        val pxSize = it.groupValues[1].toInt()
        JBUIScale.scale(pxSize).toString() + "px"
      }
      .replace(LAF_COLOR_REGEX) {
        val colorCode = it.groupValues[1].removeSurrounding("\"")
        ColorUtil.toHtmlColor(JBColor.namedColor(colorCode))
      }
  }

  /**
   * Get custom editor styles
   */
  fun getCssForCurrentEditorScheme(): String {
    val editorColorsScheme = EditorColorsManager.getInstance().globalScheme
    return editorCss
      .replace(EDITOR_FONT_FAMILY_STUB, editorColorsScheme.getFont(EditorFontType.PLAIN).family)
      .replace(EDITOR_FOREGROUND_STUB, ColorUtil.toHtmlColor(editorColorsScheme.defaultForeground))
      .replace(EDITOR_BACKGROUND_STUB, ColorUtil.toHtmlColor(editorColorsScheme.defaultBackground))
  }
}

@Suppress("CssInvalidFunction", "CssInvalidPropertyValue")
//language=CSS
private val defaultOverridesCss = """
body {
    color: LAF_COLOR("Label.foreground");
}

small {
    font-size: small;
}

p {
    margin-top: 15px;
}

h1, h2, h3, h4, h5, h6 {
    margin: 10px 0;
}

a {
    color: LAF_COLOR("Link.activeForeground");
    text-decoration: none;
}

address {
    color: LAF_COLOR("Link.activeForeground");
    text-decoration: none;
}

code {
    font-size: medium;
}

blockquote {
    margin: 5px 0;
}

table {
    border: none;
}

td {
    border: none;
    padding: 3px;
}

th {
    border: none;
    padding: 3px;
}

pre {
    margin: 5px 0;
}

menu {
    margin-top: 10px;
    margin-bottom: 10px;
    margin-left-ltr: 40px;
    margin-right-rtl: 40px;
}

dir {
    margin-top: 10px;
    margin-bottom: 10px;
    margin-left-ltr: 40px;
    margin-right-rtl: 40px;
}

dd {
    margin-left-ltr: 40px;
    margin-right-rtl: 40px;
}

dl {
    margin: 10px 0;
}

ol {
    margin-top: 10px;
    margin-bottom: 10px;
    margin-left-ltr: 22px;
    margin-right-rtl: 22px;
}

ul {
    margin-top: 10px;
    margin-bottom: 10px;
    margin-left-ltr: 12px;
    margin-right-rtl: 12px;
    -bullet-gap: 10px;
}

ul li p {
    margin-top: 0;
}

ul li ul {
    margin-left-ltr: 25px;
    margin-right-rtl: 25px;
}

ul li ul li ul {
    margin-left-ltr: 25px;
    margin-right-rtl: 25px;
}

ul li menu {
    margin-left-ltr: 25px;
    margin-right-rtl: 25px;
}
""".trimIndent()

//language=CSS
private val editorCss = """
  code {
      font-family: ${EDITOR_FONT_FAMILY_STUB};
  }
  
  pre {
      font-family: ${EDITOR_FONT_FAMILY_STUB};
  }
  
  .editor-color {
      color: ${EDITOR_FOREGROUND_STUB};
  }
  
  .editor-background {
      background-color: ${EDITOR_BACKGROUND_STUB};
  }
    
  """.trimIndent()

@Suppress("CssInvalidFunction")
//language=CSS
private val customCss = """
blockquote p {
    border-left: 2px solid LAF_COLOR("Component.borderColor");
    padding-left: 10px;
}
""".trimIndent()
