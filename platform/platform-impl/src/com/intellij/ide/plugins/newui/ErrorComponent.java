// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

/**
 * @author Alexander Lobas
 */
public final class ErrorComponent {
  private static final String KEY = "EnableCallback";

  @NotNull
  public static JComponent create(@NotNull JPanel panel, @Nullable Object constraints) {
    JEditorPane editorPane = new JEditorPane();
    panel.add(editorPane, constraints);

    convertToLabel(editorPane);

    HTMLEditorKit kit = UIUtil.getHTMLEditorKit();
    StyleSheet sheet = kit.getStyleSheet();
    sheet.addRule("span {color: " + ColorUtil.toHtmlColor(DialogWrapper.ERROR_FOREGROUND_COLOR) + "}");
    sheet.addRule("a {color: " + ColorUtil.toHtmlColor(JBUI.CurrentTheme.Link.linkColor()) + "}");
    editorPane.setEditorKit(kit);

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

  public static void convertToLabel(@NotNull JEditorPane editorPane) {
    editorPane.setEditable(false);
    editorPane.setFocusable(false);
    editorPane.setOpaque(false);
    editorPane.setBorder(null);
    editorPane.setContentType("text/html");
    editorPane.setCaret(EmptyCaret.INSTANCE);
  }

  public static void show(@NotNull JComponent errorComponent,
                          @NotNull String message,
                          @Nullable String action,
                          @Nullable Runnable enableCallback) {
    JEditorPane editorPane = (JEditorPane)errorComponent;

    editorPane.setText("<html><span>" + StringUtil.escapeXmlEntities(message) + "</span>" +
                       (enableCallback == null ? "" : "&nbsp;<a href='link'>" + action + "</a>") + "</html>");

    editorPane.putClientProperty(KEY, enableCallback);
  }

  @NotNull
  public static JComponent show(@NotNull JPanel panel,
                                @Nullable Object constraints,
                                @Nullable JComponent errorComponent,
                                @NotNull String message,
                                @Nullable String action,
                                @Nullable Runnable enableCallback) {
    JComponent component = errorComponent == null ? create(panel, constraints) : errorComponent;
    show(component, message, action, enableCallback);
    return component;
  }
}