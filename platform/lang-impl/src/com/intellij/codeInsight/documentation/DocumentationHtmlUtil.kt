// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation

import com.intellij.lang.documentation.DocumentationMarkup.*
import com.intellij.openapi.module.ModuleTypeManager
import com.intellij.openapi.module.UnknownModuleType
import com.intellij.ui.ColorUtil
import com.intellij.ui.components.JBHtmlPaneStyleSheetRulesProvider
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StyleSheetUtil
import com.intellij.util.ui.UIUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus
import java.util.function.Function
import javax.swing.Icon
import javax.swing.text.html.StyleSheet

@ApiStatus.Internal
object DocumentationHtmlUtil {

  @JvmStatic
  val contentOuterPadding: Int get() = 10

  // Should be minimum 2 to compensate mandatory table border width of 2
  @JvmStatic
  val contentInnerPadding: Int get() = 2

  @JvmStatic
  val spaceBeforeParagraph: Int get() = JBHtmlPaneStyleSheetRulesProvider.spaceBeforeParagraph

  @JvmStatic
  val spaceAfterParagraph: Int get() = JBHtmlPaneStyleSheetRulesProvider.spaceAfterParagraph

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
  fun getModuleIconResolver(baseIconResolver: Function<in String?, out Icon?>): (String) -> Icon? = { key: String ->
    baseIconResolver.apply(key)
    ?: ModuleTypeManager.getInstance().findByID(key)
      .takeIf { it !is UnknownModuleType }
      ?.icon
  }

  @JvmStatic
  fun getDocumentationPaneAdditionalCssRules(): List<StyleSheet> {
    val linkColor = ColorUtil.toHtmlColor(JBUI.CurrentTheme.Link.Foreground.ENABLED)
    val borderColor = ColorUtil.toHtmlColor(UIUtil.getTooltipSeparatorColor())
    val sectionColor = ColorUtil.toHtmlColor(DocumentationComponent.SECTION_COLOR)

    // When updating styles here, consider updating styles in DocRenderer#getStyleSheet
    val contentOuterPadding = scale(contentOuterPadding)
    val beforeSpacing = scale(spaceBeforeParagraph)
    val afterSpacing = scale(spaceAfterParagraph)
    val contentInnerPadding = scale(contentInnerPadding)

    @Suppress("CssUnusedSymbol")
    @Language("CSS")
    val result = """
        html { padding: 0 ${contentOuterPadding}px 0 ${contentOuterPadding}px; margin: 0 }
        body { padding: 0; margin: 0; overflow-wrap: anywhere;}
        pre  { white-space: pre-wrap; }
        a { color: $linkColor; text-decoration: none;}
        .$CLASS_DEFINITION, .$CLASS_DEFINITION_SEPARATED {    
          padding: ${beforeSpacing}px ${contentInnerPadding}px ${afterSpacing}px ${contentInnerPadding}px;
        }
        .$CLASS_DEFINITION pre, .$CLASS_DEFINITION_SEPARATED pre { 
          margin: 0; padding: 0;
        }
        .$CLASS_CONTENT, .$CLASS_CONTENT_SEPARATED {
          padding: 0 ${contentInnerPadding}px 0px ${contentInnerPadding}px;
          max-width: 100%;
        }
        .$CLASS_SEPARATED, .$CLASS_DEFINITION_SEPARATED, .$CLASS_CONTENT_SEPARATED {
          padding-bottom: ${beforeSpacing + afterSpacing}px;
          margin-bottom: ${afterSpacing}px;
          border-bottom: thin solid $borderColor;
        }
        .$CLASS_BOTTOM, .$CLASS_DOWNLOAD_DOCUMENTATION, .$CLASS_TOP { 
          padding: ${beforeSpacing}px ${contentInnerPadding}px ${afterSpacing}px ${contentInnerPadding}px;
        }
        
        .$CLASS_SECTIONS { padding: 0 ${contentInnerPadding - 2}px 0 ${contentInnerPadding - 2}px 0; border-spacing: 0; }
        .$CLASS_SECTION { color: $sectionColor; padding-right: 4px; white-space: nowrap; }
      """.trimIndent()
    return listOf(StyleSheetUtil.loadStyleSheet(result))
  }
}
