/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Function;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.keymap.KeymapUtil.getFirstKeyboardShortcutText;
import static java.awt.event.InputEvent.CTRL_MASK;
import static javax.swing.KeyStroke.getKeyStroke;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS;

public final class ExpandableTextField extends JTextField implements Expandable {
  private final Function<String, String> parser;
  private final Function<String, String> joiner;
  private JBPopup popup;
  private String title;

  public ExpandableTextField() {
    this(false);
  }

  public ExpandableTextField(boolean colon) {
    this(colon ? ParametersListUtil.COLON_LINE_PARSER : ParametersListUtil.DEFAULT_LINE_PARSER,
         colon ? ParametersListUtil.COLON_LINE_JOINER : ParametersListUtil.DEFAULT_LINE_JOINER);
  }

  public ExpandableTextField(@NotNull Function<String, List<String>> parser, @NotNull Function<List<String>, String> joiner) {
    super(20);
    putClientProperty("JTextField.variant", VARIANT);
    UIUtil.addUndoRedoActions(this);
    this.parser = text -> StringUtil.join(parser.fun(text), "\n");
    this.joiner = text -> joiner.fun(Arrays.asList(StringUtil.splitByLines(text)));
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  @Override
  public void collapse() {
    if (popup != null) popup.cancel();
  }

  @Override
  public void expand() {
    if (popup != null) return;

    Font font = getFont();
    FontMetrics metrics = font == null ? null : getFontMetrics(font);
    int height = metrics == null ? 16 : metrics.getHeight();
    Dimension size = new Dimension(height * 32, height * 16);

    JTextArea area = new JTextArea(parser.fun(getText()));
    area.setFont(font);
    area.setCaretPosition(0);
    area.setWrapStyleWord(true);
    area.setLineWrap(true);
    area.putClientProperty(Expandable.class, this);
    UIUtil.addUndoRedoActions(area);

    JBScrollPane pane = new JBScrollPane(area);
    pane.setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_ALWAYS);
    pane.getVerticalScrollBar().add(JBScrollBar.LEADING, new JLabel(AllIcons.General.CollapseComponent) {{
      String text = getFirstKeyboardShortcutText("CollapseExpandableComponent");
      setToolTipText(text.isEmpty() ? "Collapse" : "Collapse (" + text + ")");
      setBorder(JBUI.Borders.empty(5, 0, 1, 5));
      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent event) {
          setIcon(AllIcons.General.CollapseComponentHover);
        }

        @Override
        public void mouseExited(MouseEvent event) {
          setIcon(AllIcons.General.CollapseComponent);
        }

        @Override
        public void mousePressed(MouseEvent event) {
          collapse();
        }
      });
    }});

    Insets insets = getInsets();
    JBInsets.addTo(size, insets);
    JBInsets.addTo(size, pane.getInsets());
    if (size.width < getWidth()) size.width = getWidth();
    pane.setPreferredSize(size);
    pane.setViewportBorder(BorderFactory.createEmptyBorder(insets.top, insets.left, insets.bottom, insets.right));

    popup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(pane, area)
      .setFocusable(true)
      .setRequestFocus(true)
      .setTitle(title)
      .setLocateByContent(true)
      .setCancelOnWindowDeactivation(false)
      .setKeyboardActions(Collections.singletonList(Pair.create(event -> {
        collapse();
        Window window = UIUtil.getWindow(this);
        if (window != null) {
          window.dispatchEvent(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), CTRL_MASK, KeyEvent.VK_ENTER, '\r'));
        }
      }, getKeyStroke(KeyEvent.VK_ENTER, CTRL_MASK))))
      .setCancelCallback(() -> {
        try {
          setText(joiner.fun(area.getText()));
          setCaretPosition(0);
          UIUtil.resetUndoRedoActions(this);
          popup = null;
          return true;
        }
        catch (Exception ignore) {
          return false;
        }
      }).createPopup();
    popup.show(new RelativePoint(this, new Point(0, 0)));
  }
}
