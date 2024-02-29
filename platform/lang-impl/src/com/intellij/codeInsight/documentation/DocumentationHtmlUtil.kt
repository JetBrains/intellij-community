// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation

import com.intellij.lang.documentation.DocumentationMarkup.*
import com.intellij.lang.documentation.QuickDocHighlightingHelper.getDefaultDocCodeStyles
import com.intellij.lang.documentation.QuickDocHighlightingHelper.getDefaultFormattingStyles
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.module.ModuleTypeManager
import com.intellij.openapi.module.UnknownModuleType
import com.intellij.ui.ColorUtil
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.ExtendableHTMLViewFactory
import com.intellij.util.ui.ExtendableHTMLViewFactory.Extensions.icons
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract
import java.awt.Color
import java.util.function.Function
import javax.swing.Icon

@ApiStatus.Internal
object DocumentationHtmlUtil {

  @JvmStatic
  val contentOuterPadding: Int get() = 10

  // Should be minimum 2 to compensate mandatory table border width of 2
  @JvmStatic
  val contentInnerPadding: Int get() = 2

  @JvmStatic
  val contentSpacing: Int get() = 8

  @JvmStatic
  val docPopupPreferredMinWidth: Int get() = 300

  @JvmStatic
  val docPopupPreferredMaxWidth: Int get() = 500

  @JvmStatic
  val docPopupMinWidth: Int get() = 300

  @JvmStatic
  val docPopupMaxWidth: Int get() = 900

  @JvmStatic
  val docPopupMaxHeight: Int get() = 500

  @JvmStatic
  val lookupDocPopupWidth: Int get() = 450

  @JvmStatic
  val lookupDocPopupMinHeight: Int get() = 300

  @JvmStatic
  fun getIconsExtension(iconResolver: Function<in String?, out Icon?>): ExtendableHTMLViewFactory.Extension {
    return icons { key: String? ->
      val resolved = iconResolver.apply(key)
      if (resolved != null) {
        return@icons resolved
      }
      val moduleType = ModuleTypeManager.getInstance().findByID(key)
      if (moduleType is UnknownModuleType
      ) null
      else moduleType.icon
    }
  }

  @JvmStatic
  fun getDocumentationPaneDefaultCssRules(background: Color): List<String> {
    val linkColor = ColorUtil.toHtmlColor(JBUI.CurrentTheme.Link.Foreground.ENABLED)
    val borderColor = ColorUtil.toHtmlColor(UIUtil.getTooltipSeparatorColor())
    val sectionColor = ColorUtil.toHtmlColor(DocumentationComponent.SECTION_COLOR)

    // When updating styles here, consider updating styles in DocRenderer#getStyleSheet
    val contentOuterPadding = scale(contentOuterPadding)
    val contentSpacing = scale(contentSpacing)
    val contentInnerPadding = scale(contentInnerPadding)

    @Suppress("CssUnusedSymbol")
    @Language("CSS")
    val result = ContainerUtil.newLinkedList(
      """
        html { padding: ${contentOuterPadding}px ${contentOuterPadding}px 0 ${contentOuterPadding}px; margin: 0 }
        body { padding: 0; margin: 0; overflow-wrap: anywhere;}
        pre  { white-space: pre-wrap; }
        a { color: $linkColor; text-decoration: none;}
        .$CLASS_DEFINITION, .$CLASS_DEFINITION_SEPARATED {    
          padding: 0 ${contentInnerPadding}px ${contentSpacing}px ${contentInnerPadding}px;
        }
        .$CLASS_DEFINITION pre, .$CLASS_DEFINITION_SEPARATED pre { 
          margin: 0; padding: 0;
        }
        .$CLASS_CONTENT, .$CLASS_CONTENT_SEPARATED {
          padding: 0 ${contentInnerPadding}px 0px ${contentInnerPadding}px;
          max-width: 100%;
        }
        .$CLASS_SEPARATED, .$CLASS_DEFINITION_SEPARATED, .$CLASS_CONTENT_SEPARATED {
          margin-bottom: ${contentSpacing}px;
          border-bottom: thin solid $borderColor;
        }
        .$CLASS_BOTTOM, .$CLASS_DOWNLOAD_DOCUMENTATION, .$CLASS_TOP { 
          padding: 0 ${contentInnerPadding}px ${contentSpacing}px ${contentInnerPadding}px;
        }
        .$CLASS_GRAYED { color: #909090; display: inline;}
        
        .$CLASS_SECTIONS { padding: 0 ${contentInnerPadding - 2}px 0 ${contentInnerPadding - 2}px 0; border-spacing: 0; }
        .$CLASS_SECTION { color: $sectionColor; padding-right: 4px; white-space: nowrap; }
      """.trimIndent()
    )

    // Styled code
    val globalScheme = EditorColorsManager.getInstance().globalScheme
    result.addAll(getDefaultDocCodeStyles(globalScheme, background, 0, contentSpacing))
    result.addAll(getDefaultFormattingStyles(0, contentSpacing))
    return result
  }

  @JvmStatic
  @Contract(pure = true)
  fun addWordBreaks(text: String): String {
    val codePoints = text.codePoints().iterator()
    if (!codePoints.hasNext()) return ""
    val result = StringBuilder(text.length + 50)
    var codePoint = codePoints.nextInt()
    val tagName = StringBuilder()

    fun next(builder: StringBuilder = result) {
      builder.appendCodePoint(codePoint)
      codePoint = if (codePoints.hasNext())
        codePoints.nextInt()
      else
        -1
    }

    while (codePoint >= 0) {
      // break after dot if surrounded by letters
      when {
        Character.isLetter(codePoint) -> {
          next()
          if (codePoint == '.'.code) {
            next()
            if (Character.isLetter(codePoint)) {
              result.append("<wbr>")
            }
          }
        }
        // break after ], ) or / followed by a char or digit
        codePoint == ')'.code || codePoint == ']'.code || codePoint == '/'.code -> {
          next()
          if (Character.isLetterOrDigit(codePoint)) {
            result.append("<wbr>")
          }
        }
        // skip tag
        codePoint == '<'.code -> {
          next()
          if (codePoint == '/'.code)
            next()
          if (!Character.isLetter(codePoint))
            continue
          tagName.clear()
          while (Character.isLetterOrDigit(codePoint) || codePoint == '-'.code) {
            next(tagName)
          }
          result.append(tagName)
          if (tagName.contentEquals("style", true)
              || tagName.contentEquals("title", true)
              || tagName.contentEquals("script", true)) {
            val curTag = tagName.toString()
            do {
              if (codePoint == '<'.code) {
                next()
                if (codePoint == '/'.code) {
                  next()
                  tagName.clear()
                  while (Character.isLetterOrDigit(codePoint) || codePoint == '-'.code) {
                    next(tagName)
                  }
                  result.append(tagName)
                  if (tagName.contentEquals(curTag, true)) {
                    while (codePoint >= 0 && codePoint != '>'.code) {
                      next()
                    }
                    break
                  }
                }
              }
              else next()
            }
            while (true)
          }
          else {
            while (codePoint >= 0) {
              when (codePoint) {
                '>'.code -> {
                  next()
                  break
                }
                '\''.code, '"'.code -> {
                  val quoteStyle = codePoint
                  next()
                  while (codePoint >= 0) {
                    when (codePoint) {
                      '\\'.code -> {
                        next()
                        if (codePoint >= 0)
                          next()
                      }
                      quoteStyle -> {
                        next()
                        break
                      }
                      else -> next()
                    }
                  }
                }
                else -> next()
              }
            }
          }
        }
        else -> next()
      }
    }

    return result.toString()
  }
}
