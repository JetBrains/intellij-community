// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components.fields;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.Expandable;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Function;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS;

/**
 * @author Sergey Malenkov
 */
public class ExpandableTextField extends ExtendableTextField implements Expandable {
  private final ExpandableSupport support;

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
    Function<? super String, String> onShow = text -> StringUtil.join(parser.fun(text), "\n");
    Function<? super String, String> onHide = text -> joiner.fun(asList(StringUtil.splitByLines(text)));
    support = new ExpandableSupport<JTextComponent>(this, onShow, onHide) {
      @NotNull
      @Override
      protected Content prepare(@NotNull JTextComponent field, @NotNull Function<? super String, String> onShow) {
        Font font = field.getFont();
        FontMetrics metrics = font == null ? null : field.getFontMetrics(font);
        int height = metrics == null ? 16 : metrics.getHeight();
        Dimension size = new Dimension(height * 32, height * 16);

        JTextArea area = new JTextArea(onShow.fun(field.getText()));
        area.putClientProperty(Expandable.class, this);
        area.setEditable(field.isEditable());
        area.setBackground(field.getBackground());
        area.setForeground(field.getForeground());
        area.setFont(font);
        area.setWrapStyleWord(true);
        area.setLineWrap(true);
        copyCaretPosition(field, area);
        UIUtil.addUndoRedoActions(area);

        JLabel label = createLabel(createCollapseExtension());
        label.setBorder(JBUI.Borders.empty(5, 0, 5, 5));

        JBScrollPane pane = new JBScrollPane(area);
        pane.setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_ALWAYS);
        pane.getVerticalScrollBar().add(JBScrollBar.LEADING, label);
        pane.getVerticalScrollBar().setBackground(area.getBackground());

        Insets insets = field.getInsets();
        Insets margin = field.getMargin();
        if (margin != null) {
          insets.top += margin.top;
          insets.left += margin.left;
          insets.right += margin.right;
          insets.bottom += margin.bottom;
        }

        JBInsets.addTo(size, insets);
        JBInsets.addTo(size, pane.getInsets());
        pane.setPreferredSize(size);
        pane.setViewportBorder(insets != null
                               ? createEmptyBorder(insets.top, insets.left, insets.bottom, insets.right)
                               : createEmptyBorder());
        return new Content() {
          @NotNull
          @Override
          public JComponent getContentComponent() {
            return pane;
          }

          @Override
          public JComponent getFocusableComponent() {
            return area;
          }

          @Override
          public void cancel(@NotNull Function<? super String, String> onHide) {
            if (field.isEditable()) {
              field.setText(onHide.fun(area.getText()));
              copyCaretPosition(area, field);
            }
          }
        };
      }
    };
    putClientProperty("monospaced", true);
    setExtensions(createExtensions());
  }

  @NotNull
  protected List<ExtendableTextComponent.Extension> createExtensions() {
    return singletonList(support.createExpandExtension());
  }

  public String getTitle() {
    return support.getTitle();
  }

  public void setTitle(String title) {
    support.setTitle(title);
  }

  @Override
  public void setEnabled(boolean enabled) {
    if (!enabled) support.collapse();
    super.setEnabled(enabled);
  }

  @Override
  public void collapse() {
    support.collapse();
  }

  @Override
  public boolean isExpanded() {
    return support.isExpanded();
  }

  @Override
  public void expand() {
    support.expand();
  }

  private static void copyCaretPosition(JTextComponent source, JTextComponent destination) {
    try {
      destination.setCaretPosition(source.getCaretPosition());
    }
    catch (Exception ignored) {
    }
  }
}
