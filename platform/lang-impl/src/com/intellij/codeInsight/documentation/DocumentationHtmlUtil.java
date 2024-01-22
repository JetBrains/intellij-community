// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.documentation;

import com.intellij.lang.documentation.QuickDocCodeHighlightingHelper;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.module.UnknownModuleType;
import com.intellij.ui.ColorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ExtendableHTMLViewFactory;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Function;

import static com.intellij.codeInsight.documentation.DocumentationComponent.SECTION_COLOR;

@Internal
public final class DocumentationHtmlUtil {

  private DocumentationHtmlUtil() {
  }

  public static ExtendableHTMLViewFactory.Extension getIconsExtension(@NotNull Function<? super @NotNull String, ? extends @Nullable Icon> iconResolver) {
    return ExtendableHTMLViewFactory.Extensions.icons(key -> {
      Icon resolved = iconResolver.apply(key);
      if (resolved != null) {
        return resolved;
      }
      ModuleType<?> moduleType = ModuleTypeManager.getInstance().findByID(key);
      return moduleType instanceof UnknownModuleType
             ? null
             : moduleType.getIcon();
    });
  }

  public static @NotNull List<String> getDocumentationPaneDefaultCssRules(Color background) {
    int leftPadding = 8;
    int definitionTopPadding = 4;
    String linkColor = ColorUtil.toHtmlColor(JBUI.CurrentTheme.Link.Foreground.ENABLED);
    String borderColor = ColorUtil.toHtmlColor(UIUtil.getTooltipSeparatorColor());
    String sectionColor = ColorUtil.toHtmlColor(SECTION_COLOR);
    int fontSize = StartupUiUtil.getLabelFont().getSize();

    // When updating styles here, consider updating styles in DocRenderer#getStyleSheet
    var result = ContainerUtil.newLinkedList(
      "html { padding-bottom: 8px; }",
      "h5, h6 { margin-top: 8px; margin-bottom: 0 }",
      "h4 { margin-top: 8px; margin-bottom: 0; font-size: " + fontSize + "}",
      "h3 { margin-top: 8px; margin-bottom: 0; font-size: " + (fontSize + 3) + "}",
      "h2 { margin-top: 8px; margin-bottom: 0; font-size: " + (fontSize + 5) + "}",
      "h1 { margin-top: 8px; margin-bottom: 0; font-size: " + (fontSize + 9) + "}",
      "h0 { margin-top: 8px; margin-bottom: 0; font-size: " + (fontSize + 12) + "}",
      "a { color: " + linkColor + "; text-decoration: none;}",
      ".definition { padding: " + definitionTopPadding + "px 17px 1px " + leftPadding + "px;" +
      "              border-bottom: thin solid " + borderColor + "; }",
      ".definition-only { padding: " + definitionTopPadding + "px 17px 0 " + leftPadding + "px; }",
      ".definition-only pre { margin-bottom: 0 }",
      ".content { padding: 5px 16px 0 " + leftPadding + "px; max-width: 100% }",
      ".content-separated { padding: 5px 16px 5px " + leftPadding + "px; max-width: 100%;" +
      "                     border-bottom: thin solid " + borderColor + "; }",
      ".content-only { padding: 8px 16px 0 " + leftPadding + "px; max-width: 100% }",
      ".separated { padding: 0 0 4px 0; margin: 0; max-width: 100%;" +
      "             border-bottom: thin solid " + borderColor + "; }",
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

    // Styled code
    EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
    result.addAll(QuickDocCodeHighlightingHelper.getDefaultDocCodeStyles(globalScheme, background));

    return result;
  }

}
