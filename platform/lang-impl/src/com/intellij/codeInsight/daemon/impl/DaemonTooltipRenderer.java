// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.hint.LineTooltipRenderer;
import com.intellij.codeInsight.hint.TooltipLinkHandlerEP;
import com.intellij.codeInspection.ui.InspectionNodeInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.NlsContexts.Tooltip;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.Html;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.awt.*;
import java.util.List;

class DaemonTooltipRenderer extends LineTooltipRenderer {
  private static final @NonNls String END_MARKER = "<!-- end marker -->";

  DaemonTooltipRenderer(@Tooltip @Nullable String text, int width, Object @NotNull [] comparable) {
    super(text, width, comparable);
  }

  @Override
  protected @NotNull @Tooltip String dressDescription(@NotNull Editor editor, @NotNull @Tooltip String tooltipText, boolean expand) {
    if (!expand) {
      return super.dressDescription(editor, tooltipText, false);
    }

    List<@Tooltip String> problems = getProblems(tooltipText);
    @Tooltip StringBuilder text = new StringBuilder();
    for (@Tooltip String problem : problems) {
      String ref = getLinkRef(problem);
      if (ref != null) {
        String description = TooltipLinkHandlerEP.getDescription(ref, editor);
        if (description != null) {
          description =
            InspectionNodeInfo.stripUIRefsFromInspectionDescription(UIUtil.getHtmlBody(new Html(description).setKeepFont(true)));
          text
            .append(getHtmlForProblemWithLink(problem))
            .append(END_MARKER)
            .append("<p>")
            .append("<span style=\"color:")
            .append(ColorUtil.toHex(getDescriptionTitleColor()))
            .append("\">")
            .append(TooltipLinkHandlerEP.getDescriptionTitle(ref, editor))
            .append(":</span> ")
            .append(description)
            .append(UIUtil.BORDER_LINE);
        }
      }
      else {
        text.append(UIUtil.getHtmlBody(new Html(problem).setKeepFont(true))).append(UIUtil.BORDER_LINE);
      }
    }
    if (!text.isEmpty()) { //otherwise do not change anything
      return XmlStringUtil.wrapInHtml(StringUtil.trimEnd(text.toString(), UIUtil.BORDER_LINE));
    }
    return super.dressDescription(editor, tooltipText, true);
  }

  protected @Unmodifiable @NotNull List<@Tooltip String> getProblems(@NotNull @Tooltip String tooltipText) {
    return StringUtil.split(UIUtil.getHtmlBody(new Html(tooltipText).setKeepFont(true)), UIUtil.BORDER_LINE);
  }

  protected @NotNull @Tooltip String getHtmlForProblemWithLink(@NotNull @Tooltip String problem) {
    Html html = new Html(problem).setKeepFont(true);
    return UIUtil.getHtmlBody(html)
                 .replace(DaemonBundle.message("inspection.extended.description"), DaemonBundle.message("inspection.collapse.description"));
  }

  protected static @Nullable String getLinkRef(@NonNls String text) {
    final String linkWithRef = "<a href=\"";
    int linkStartIdx = text.indexOf(linkWithRef);
    if (linkStartIdx >= 0) {
      String ref = text.substring(linkStartIdx + linkWithRef.length());
      int quoteIdx = ref.indexOf('"');
      if (quoteIdx > 0) {
        return ref.substring(0, quoteIdx);
      }
    }
    return null;
  }

  protected @NotNull Color getDescriptionTitleColor() {
    return JBColor.namedColor("ToolTip.infoForeground", new JBColor(0x919191, 0x919191));
  }

  @Override
  public @NotNull LineTooltipRenderer createRenderer(@Nullable String text, int width) {
    return new DaemonTooltipRenderer(text, width, getEqualityObjects());
  }
}
