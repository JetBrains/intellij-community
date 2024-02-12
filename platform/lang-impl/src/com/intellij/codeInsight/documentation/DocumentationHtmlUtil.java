// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation;

import com.intellij.lang.documentation.QuickDocHighlightingHelper;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.module.UnknownModuleType;
import com.intellij.ui.ColorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ExtendableHTMLViewFactory;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Function;

import static com.intellij.codeInsight.documentation.DocumentationComponent.SECTION_COLOR;
import static com.intellij.lang.documentation.DocumentationMarkup.*;

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
    int leftPadding = 0;
    String linkColor = ColorUtil.toHtmlColor(JBUI.CurrentTheme.Link.Foreground.ENABLED);
    String borderColor = ColorUtil.toHtmlColor(UIUtil.getTooltipSeparatorColor());
    String sectionColor = ColorUtil.toHtmlColor(SECTION_COLOR);

    // When updating styles here, consider updating styles in DocRenderer#getStyleSheet
    var result = ContainerUtil.newLinkedList(
      "html { padding: 10px; margin: 0px }",
      "body { padding: 0px; margin: 0px }",
      "pre  {white-space: pre-wrap}",  // supported by JetBrains Runtime
      "a { color: " + linkColor + "; text-decoration: none;}",
      "." + CLASS_DEFINITION + " { padding: 0px 17px 8px " + leftPadding + "px;" +
      "              margin-bottom: 2px;" +
      "              border-bottom: thin solid " + borderColor + "; }",
      "." + CLASS_DEFINITION + " pre { margin: 0px; padding: 0px; }",
      "." + CLASS_DEFINITION_ONLY + " { padding: 0px 10px 0 " + leftPadding + "px; }",
      "." + CLASS_DEFINITION_ONLY + " pre { margin: 0px; padding: 0px }",
      "." + CLASS_CONTENT + " { padding: 5px 16px 0 " + leftPadding + "px; max-width: 100% }",
      "." + CLASS_CONTENT_SEPARATED + " { padding: 5px 16px 5px " + leftPadding + "px; max-width: 100%;" +
      "                     border-bottom: thin solid " + borderColor + "; }",
      "." + CLASS_CONTENT_ONLY + " { padding: 0px 16px 0 " + leftPadding + "px; max-width: 100% }",
      "." + CLASS_SEPARATED + " { padding: 0 0 4px 0; margin: 0; max-width: 100%;" +
      "             border-bottom: thin solid " + borderColor + "; }",
      "." + CLASS_BOTTOM + " { padding: 3px 16px 0 " + leftPadding + "px; }",
      "." + CLASS_DOWNLOAD_DOCUMENTATION + " { padding: 0px 0px 18px; }",
      "." + CLASS_BOTTOM_NO_CONTENT + " { padding: 5px 16px 0 " + leftPadding + "px; }",
      "." + CLASS_GREYED + " { color: #909090; display: inline;}",

      // sections table
      "." + CLASS_SECTIONS + " { padding: 0 16px 0 " + leftPadding + "px; border-spacing: 0; }",
      "." + CLASS_SECTION + " { color: " + sectionColor + "; padding-right: 4px; white-space:nowrap;}"
    );

    // Styled code
    EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
    result.addAll(QuickDocHighlightingHelper.getDefaultDocCodeStyles(globalScheme, background));
    result.addAll(QuickDocHighlightingHelper.getDefaultFormattingStyles());
    return result;
  }
}
