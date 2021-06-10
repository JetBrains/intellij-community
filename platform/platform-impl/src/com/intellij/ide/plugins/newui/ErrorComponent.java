// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

/**
 * @author Alexander Lobas
 */
final class ErrorComponent {
  private static final String KEY = "EnableCallback";

  static @NotNull JComponent create(@NotNull JPanel panel, @Nullable Object constraints) {
    JEditorPane editorPane = new JEditorPane();
    panel.add(editorPane, constraints);

    UIUtil.convertToLabel(editorPane);
    editorPane.setCaret(EmptyCaret.INSTANCE);

    StyleSheet sheet = ((HTMLEditorKit)editorPane.getEditorKit()).getStyleSheet();
    sheet.addRule("span {color: " + ColorUtil.toHtmlColor(DialogWrapper.ERROR_FOREGROUND_COLOR) + "}");
    sheet.addRule("a {color: " + ColorUtil.toHtmlColor(JBUI.CurrentTheme.Link.Foreground.ENABLED) + "}");

    editorPane.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        Object callback = editorPane.getClientProperty(KEY);
        if (callback instanceof Runnable) {
          ApplicationManager.getApplication().invokeLater((Runnable)callback, ModalityState.any());
        }
      }
    });

    return editorPane;
  }

  static void show(@NotNull JComponent errorComponent,
                   @NotNull @Nls String message,
                   @Nullable @Nls String action,
                   @Nullable Runnable enableCallback) {
    JEditorPane editorPane = (JEditorPane)errorComponent;

    HtmlChunk.Element html = HtmlChunk.html().children(HtmlChunk.span().addText(message));
    if (enableCallback != null) {
      html = html.children(HtmlChunk.nbsp(), HtmlChunk.link("link", action));
    }
    editorPane.setText(html.toString());

    editorPane.putClientProperty(KEY, enableCallback);
  }

  static @NotNull JComponent show(@NotNull JPanel panel,
                                  @Nullable Object constraints,
                                  @Nullable JComponent errorComponent,
                                  @NotNull @Nls String message,
                                  @Nullable @Nls String action,
                                  @Nullable Runnable enableCallback) {
    JComponent component = errorComponent == null ? create(panel, constraints) : errorComponent;
    show(component, message, action, enableCallback);
    return component;
  }
}