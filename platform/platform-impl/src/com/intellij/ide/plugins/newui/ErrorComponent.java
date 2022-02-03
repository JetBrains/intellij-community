// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Alexander Lobas
 */
final class ErrorComponent extends JEditorPane {

  private static final String KEY = "EnableCallback";

  ErrorComponent() {
    UIUtil.convertToLabel(this);
    setCaret(EmptyCaret.INSTANCE);

    StyleSheet sheet = ((HTMLEditorKit)getEditorKit()).getStyleSheet();
    sheet.addRule("span {color: " + ColorUtil.toHtmlColor(UIUtil.getErrorForeground()) + "}");
    sheet.addRule("a {color: " + ColorUtil.toHtmlColor(JBUI.CurrentTheme.Link.Foreground.ENABLED) + "}");

    addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        Object callback = getClientProperty(KEY);
        if (callback instanceof Runnable) {
          ApplicationManager.getApplication().invokeLater((Runnable)callback, ModalityState.any());
        }
      }
    });
  }

  void setErrors(@NotNull List<? extends HtmlChunk> errors,
                 @NotNull Runnable enableCallback) {
    setVisible(!errors.isEmpty());

    if (isVisible()) {
      setText(toHtml(errors));
      putClientProperty(KEY, enableCallback);
    }
  }

  private static @NotNull @NlsSafe String toHtml(@NotNull List<? extends HtmlChunk> chunks) {
    List<HtmlChunk> newChunks = new ArrayList<>();
    for (Iterator<? extends HtmlChunk> iterator = chunks.iterator(); iterator.hasNext(); ) {
      newChunks.add(iterator.next());
      if (iterator.hasNext()) {
        newChunks.add(HtmlChunk.nbsp());
      }
    }

    return HtmlChunk.html().children(newChunks).toString();
  }
}