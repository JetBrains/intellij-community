// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.impl.livePreview;

import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class ReplacementView extends JPanel {

  @Override
  protected void paintComponent(@NotNull Graphics graphics) {
  }

  public ReplacementView(@Nls @Nullable String replacement) {
    if (replacement == null) {
      String htmlToShow = new HtmlBuilder()
        .append(UsageViewBundle.message("label.malformed.replacement.string"))
        .wrapWithHtmlBody()
        .toString();
      JLabel jLabel = new JBLabel(htmlToShow).setAllowAutoWrapping(true);
      jLabel.setForeground(JBColor.RED);
      add(jLabel);
    }
    else {
      @Nls String[] lines = StringUtil.shortenTextWithEllipsis(replacement, 500, 0, true).split("\n+");
      String htmlToShow = new HtmlBuilder()
        .appendWithSeparators(HtmlChunk.br(), ContainerUtil.map(lines, HtmlChunk::text))
        .wrapWithHtmlBody()
        .toString();
      JLabel jLabel = new JBLabel(htmlToShow).setAllowAutoWrapping(true);
      jLabel.setForeground(new JBColor(Gray._240, Gray._200));
      add(jLabel);
    }
  }
}
