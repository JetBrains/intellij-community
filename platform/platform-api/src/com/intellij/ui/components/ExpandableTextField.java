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
package com.intellij.ui.components;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.Expandable;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Function;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.AncestorListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;
import java.util.List;

import static com.intellij.openapi.keymap.KeymapUtil.createTooltipText;
import static java.awt.event.InputEvent.CTRL_MASK;
import static java.beans.EventHandler.create;
import static java.util.Collections.singletonList;
import static javax.swing.KeyStroke.getKeyStroke;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS;

/**
 * @author Sergey Malenkov
 */
public class ExpandableTextField extends ExtendableTextField implements Expandable {
  private static final int MINIMAL_WIDTH = 50;
  private final Function<String, String> parser;
  private final Function<String, String> joiner;
  private JBPopup popup;
  private String title;

  /**
   * Creates an expandable text field with the default line parser/joiner,
   * that uses a whitespaces to split a string to several lines.
   */
  public ExpandableTextField() {
    this(ParametersListUtil.DEFAULT_LINE_PARSER, ParametersListUtil.DEFAULT_LINE_JOINER);
  }

  /**
   * Creates an expandable text field with the specified line parser/joiner.
   *
   * @see ParametersListUtil
   */
  public ExpandableTextField(@NotNull Function<String, List<String>> parser, @NotNull Function<List<String>, String> joiner) {
    this.parser = text -> StringUtil.join(parser.fun(text), "\n");
    this.joiner = text -> joiner.fun(Arrays.asList(StringUtil.splitByLines(text)));
    addAncestorListener(create(AncestorListener.class, this, "collapse"));
    addComponentListener(create(ComponentListener.class, this, "collapse"));
    putClientProperty("monospaced", true);
    setExtensions(createExtensions());
  }

  protected List<Extension> createExtensions() {
    return singletonList(new Extension() {
      @Override
      public Icon getIcon(boolean hovered) {
        return hovered ? AllIcons.General.ExpandComponentHover : AllIcons.General.ExpandComponent;
      }

      @Override
      public Runnable getActionOnClick() {
        return ExpandableTextField.this::expand;
      }

      @Override
      public String getTooltip() {
        return createTooltipText("Expand", "ExpandExpandableComponent");
      }
    });
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
    area.setWrapStyleWord(true);
    area.setLineWrap(true);
    area.putClientProperty(Expandable.class, this);
    copyCaretPosition(this, area);
    UIUtil.addUndoRedoActions(area);

    JBScrollPane pane = new JBScrollPane(area);
    pane.setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_ALWAYS);
    pane.getVerticalScrollBar().setBackground(area.getBackground());
    pane.getVerticalScrollBar().add(JBScrollBar.LEADING, new JLabel(AllIcons.General.CollapseComponent) {{
      setToolTipText(createTooltipText("Collapse", "CollapseExpandableComponent"));
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
    if (size.width - MINIMAL_WIDTH < getWidth()) size.width = getWidth();

    Point location = new Point(0, 0);
    SwingUtilities.convertPointToScreen(location, this);
    Rectangle screen = ScreenUtil.getScreenRectangle(this);
    int bottom = screen.y - location.y + screen.height;
    if (bottom < size.height) {
      int top = location.y - screen.y + getHeight();
      if (top < bottom) {
        size.height = bottom;
      }
      else {
        if (size.height > top) size.height = top;
        location.y -= size.height - getHeight();
      }
    }
    pane.setPreferredSize(size);
    pane.setViewportBorder(BorderFactory.createEmptyBorder(insets.top, insets.left, insets.bottom, insets.right));

    popup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(pane, area)
      .setFocusable(true)
      .setRequestFocus(true)
      .setTitle(title)
      .setLocateByContent(true)
      .setCancelOnWindowDeactivation(false)
      .setKeyboardActions(singletonList(Pair.create(event -> {
        collapse();
        Window window = UIUtil.getWindow(this);
        if (window != null) {
          window.dispatchEvent(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), CTRL_MASK, KeyEvent.VK_ENTER, '\r'));
        }
      }, getKeyStroke(KeyEvent.VK_ENTER, CTRL_MASK))))
      .setCancelCallback(() -> {
        try {
          setText(joiner.fun(area.getText()));
          copyCaretPosition(area, this);
          popup = null;
          return true;
        }
        catch (Exception ignore) {
          return false;
        }
      }).createPopup();
    popup.show(new RelativePoint(location));
  }

  private static void copyCaretPosition(JTextComponent source, JTextComponent destination) {
    try {
      destination.setCaretPosition(source.getCaretPosition());
    }
    catch (Exception ignored) {
    }
  }
}
