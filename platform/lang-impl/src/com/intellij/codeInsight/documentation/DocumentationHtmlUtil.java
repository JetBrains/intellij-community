// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.documentation;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.impl.EditorCssFontResolver;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.module.UnknownModuleType;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.ui.ExtendableHTMLViewFactory;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.ImageView;
import javax.swing.text.html.StyleSheet;

import java.util.List;

import static com.intellij.codeInsight.documentation.DocumentationComponent.SECTION_COLOR;

@Internal
public final class DocumentationHtmlUtil {

  private DocumentationHtmlUtil() {
  }

  //TODO: extract to common extensions
  public static ExtendableHTMLViewFactory.Extension getHiDPIImagesExtension(@NotNull JComponent component) {
    return (element, view) -> {
      if (view instanceof ImageView) {
        // we have to work with raw image, apply scaling manually
        return new DocumentationScalingImageView(element, () -> {
          return ScaleContext.create(component);
        });
      }
      return null;
    };
  }

  public static ExtendableHTMLViewFactory.Extension getModuleIconsExtension() {
    return ExtendableHTMLViewFactory.Extensions.icons(key -> {
      ModuleType<?> moduleType = ModuleTypeManager.getInstance().findByID(key);
      return moduleType instanceof UnknownModuleType
             ? null
             : moduleType.getIcon();
    });
  }

  /**
   * Swing HTML Editor Kit processes values in percents of 'font-size' css property really weirdly
   * and even in not a cross-platform way.
   * So we have to do some hacks to align fonts.
   */
  private static int getMonospaceFontSizeCorrection() {
    return SystemInfo.isWin10OrNewer && !ApplicationManager.getApplication().isUnitTestMode() ? 96 : 100;
  }

  public static void addDocumentationPaneDefaultCssRules(@NotNull HTMLEditorKit editorKit) {
    StyleSheet styleSheet = editorKit.getStyleSheet();
    for (String rule : getDocumentationPaneDefaultCssRules()) {
      styleSheet.addRule(rule);
    }
  }

  public static @NotNull List<String> getDocumentationPaneDefaultCssRules() {
    int leftPadding = 8;
    int definitionTopPadding = 4;
    String linkColor = ColorUtil.toHtmlColor(JBUI.CurrentTheme.Link.Foreground.ENABLED);
    String borderColor = ColorUtil.toHtmlColor(UIUtil.getTooltipSeparatorColor());
    String sectionColor = ColorUtil.toHtmlColor(SECTION_COLOR);
    String editorFontStyle = "{ font-family:\"" + EditorCssFontResolver.EDITOR_FONT_NAME_NO_LIGATURES_PLACEHOLDER + "\";" +
                             "font-size:" + getMonospaceFontSizeCorrection() + "%; }";

    return List.of(
      "tt " + editorFontStyle,
      "code " + editorFontStyle,
      "pre " + editorFontStyle,
      ".pre " + editorFontStyle,

      "html { padding-bottom: 8px; }",
      "h1, h2, h3, h4, h5, h6 { margin-top: 0; padding-top: 1px; }",
      "a { color: " + linkColor + "; text-decoration: none;}",
      ".definition { padding: " + definitionTopPadding + "px 17px 1px " + leftPadding + "px;" +
      "              border-bottom: thin solid " + borderColor + "; }",
      ".definition-only { padding: " + definitionTopPadding + "px 17px 0 " + leftPadding + "px; }",
      ".definition-only pre { margin-bottom: 0 }",
      ".content { padding: 5px 16px 0 " + leftPadding + "px; max-width: 100% }",
      ".content-separated { padding: 5px 16px 5px " + leftPadding + "px; max-width: 100%;" +
      "                     border-bottom: thin solid " + borderColor + "; }",
      ".content-only { padding: 8px 16px 0 " + leftPadding + "px; max-width: 100% }",
      ".bottom { padding: 3px 16px 0 " + leftPadding + "px; }",
      ".bottom-no-content { padding: 5px 16px 0 " + leftPadding + "px; }",
      "p { padding: 1px 0 2px 0; }",
      "ol { padding: 0 16px 0 0; }",
      "ul { padding: 0 16px 0 0; }",
      "li { padding: 1px 0 2px 0; }",
      ".grayed { color: #909090; display: inline;}",
      ".centered { text-align: center}",

      // sections table
      ".sections { padding: 0 16px 0 " + leftPadding + "px; border-spacing: 0; }",
      "tr { margin: 0 0 0 0; padding: 0 0 0 0; }",
      "table p { padding-bottom: 0}",
      "td { margin: 4px 0 0 0; padding: 0 0 0 0; }",
      "th { text-align: left; }",
      "td pre { padding: 1px 0 0 0; margin: 0 0 0 0 }",
      ".section { color: " + sectionColor + "; padding-right: 4px; white-space:nowrap;}"
    );
  }
}
