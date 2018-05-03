// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.impl.actions.ShowErrorDescriptionAction;
import com.intellij.codeInsight.hint.LineTooltipRenderer;
import com.intellij.codeInsight.hint.TooltipLinkHandlerEP;
import com.intellij.codeInspection.ui.InspectionNodeInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.Html;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

class DaemonTooltipRenderer extends LineTooltipRenderer {
  @NonNls private static final String END_MARKER = "<!-- end marker -->";


  public DaemonTooltipRenderer(final String text, Object[] comparable) {
    super(text, comparable);
  }

  public DaemonTooltipRenderer(final String text, final int width, Object[] comparable) {
    super(text, width, comparable);
  }

  @Override
  protected void onHide(final JComponent contentComponent) {
    ShowErrorDescriptionAction.rememberCurrentWidth(contentComponent.getWidth());
  }

  @Override
  protected boolean dressDescription(@NotNull final Editor editor) {
    final List<String> problems = StringUtil.split(UIUtil.getHtmlBody(new Html(myText).setKeepFont(true)), UIUtil.BORDER_LINE);
    StringBuilder text = new StringBuilder();
    for (String problem : problems) {
      final String ref = getLinkRef(problem);
      if (ref != null) {
        String description = TooltipLinkHandlerEP.getDescription(ref, editor);
        if (description != null) {
          description =
            InspectionNodeInfo.stripUIRefsFromInspectionDescription(UIUtil.getHtmlBody(new Html(description).setKeepFont(true)));
          text
            .append(UIUtil.getHtmlBody(new Html(problem).setKeepFont(true)).replace(DaemonBundle.message("inspection.extended.description"),
                                                                                    DaemonBundle
                                                                                      .message("inspection.collapse.description")))
            .append(END_MARKER)
            .append("<p>")
            .append(description)
            .append(UIUtil.BORDER_LINE);
        }
      }
      else {
        text.append(UIUtil.getHtmlBody(new Html(problem).setKeepFont(true))).append(UIUtil.BORDER_LINE);
      }
    }
    if (text.length() > 0) { //otherwise do not change anything
      myText = XmlStringUtil.wrapInHtml(StringUtil.trimEnd(text.toString(), UIUtil.BORDER_LINE));
      return true;
    }
    return false;
  }

  @Nullable
  private static String getLinkRef(@NonNls String text) {
    final String linkWithRef = "<a href=\"";
    final int linkStartIdx = text.indexOf(linkWithRef);
    if (linkStartIdx >= 0) {
      final String ref = text.substring(linkStartIdx + linkWithRef.length());
      final int quoteIdx = ref.indexOf('"');
      if (quoteIdx > 0) {
        return ref.substring(0, quoteIdx);
      }
    }
    return null;
  }

  @Override
  protected void stripDescription() {
    final List<String> problems = StringUtil.split(UIUtil.getHtmlBody(new Html(myText).setKeepFont(true)), UIUtil.BORDER_LINE);
    StringBuilder text = new StringBuilder();
    for (String rawProblem : problems) {
      final String problem = StringUtil.split(rawProblem, END_MARKER).get(0);
      text.append(UIUtil.getHtmlBody(new Html(problem).setKeepFont(true)).replace(DaemonBundle.message("inspection.collapse.description"),
                                                                                  DaemonBundle.message("inspection.extended.description")))
          .append(UIUtil.BORDER_LINE);
    }
    myText = XmlStringUtil.wrapInHtml(StringUtil.trimEnd(text.toString(), UIUtil.BORDER_LINE));
  }

  @Override
  protected LineTooltipRenderer createRenderer(final String text, final int width) {
    return new DaemonTooltipRenderer(text, width, getEqualityObjects());
  }
}
