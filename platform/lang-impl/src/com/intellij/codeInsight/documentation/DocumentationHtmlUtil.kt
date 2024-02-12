// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation

import com.intellij.lang.documentation.DocumentationMarkup.*
import com.intellij.lang.documentation.QuickDocHighlightingHelper.getDefaultDocCodeStyles
import com.intellij.lang.documentation.QuickDocHighlightingHelper.getDefaultFormattingStyles
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.module.ModuleTypeManager
import com.intellij.openapi.module.UnknownModuleType
import com.intellij.ui.ColorUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.ExtendableHTMLViewFactory
import com.intellij.util.ui.ExtendableHTMLViewFactory.Extensions.icons
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.util.function.Function
import javax.swing.Icon

@ApiStatus.Internal
object DocumentationHtmlUtil {

  const val CONTENT_OUTER_PADDING = 10
  // Should be minimum 2 to compensate mandatory table border width of 2
  const val CONTENT_INNER_PADDING = 2
  const val CONTENT_SPACING = 8

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
    @Language("CSS")
    val result = ContainerUtil.newLinkedList(
      """
        html { padding: 10px 10px 0 10px; margin: 0 }
        body { padding: 0; margin: 0; }
        pre  { white-space: pre-wrap }
        a { color: $linkColor; text-decoration: none;}
        .$CLASS_DEFINITION, .$CLASS_DEFINITION_SEPARATED {    
          padding: 0 ${CONTENT_INNER_PADDING}px ${CONTENT_SPACING}px ${CONTENT_INNER_PADDING}px;
        }
        .$CLASS_DEFINITION pre, .$CLASS_DEFINITION_SEPARATED pre { 
          margin: 0; padding: 0;
        }
        .$CLASS_CONTENT, .$CLASS_CONTENT_SEPARATED {
          padding: 0 ${CONTENT_INNER_PADDING}px 0px ${CONTENT_INNER_PADDING}px;
          max-width: 100%;
        }
        .$CLASS_SEPARATED, .$CLASS_DEFINITION_SEPARATED, .$CLASS_CONTENT_SEPARATED {
          margin-bottom: ${CONTENT_SPACING}px;
          border-bottom: thin solid $borderColor;
        }
        .$CLASS_BOTTOM, .$CLASS_DOWNLOAD_DOCUMENTATION { 
          padding: 0 ${CONTENT_INNER_PADDING}px ${CONTENT_SPACING}px ${CONTENT_INNER_PADDING}px;
        }
        .$CLASS_GREYED { color: #909090; display: inline;}
        
        .$CLASS_SECTIONS { padding: 0 ${CONTENT_INNER_PADDING - 2}px 0 ${CONTENT_INNER_PADDING - 2}px 0; border-spacing: 0; }
        .$CLASS_SECTION { color: $sectionColor; padding-right: 4px; white-space: nowrap; }
      """.trimIndent()
    )

    // Styled code
    val globalScheme = EditorColorsManager.getInstance().globalScheme
    result.addAll(getDefaultDocCodeStyles(globalScheme, background, CONTENT_SPACING))
    result.addAll(getDefaultFormattingStyles(CONTENT_SPACING))
    return result
  }
}
