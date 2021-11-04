/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.ui.TextAccessor;
import com.intellij.util.BooleanFunction;
import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.TextUI;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Objects;

public class JBTextField extends JTextField implements ComponentWithEmptyText, TextAccessor {
  private TextComponentEmptyText myEmptyText;

  public JBTextField() {
    init();
  }

  public JBTextField(int columns) {
    super(columns);
    init();
  }

  public JBTextField(@Nls String text) {
    super(text);
    init();
  }

  public JBTextField(@Nls String text, int columns) {
    super(text, columns);
    init();
  }

  private void init() {
    UIUtil.addUndoRedoActions(this);
    myEmptyText = new TextComponentEmptyText(this) {
      @Override
      protected boolean isStatusVisible() {
        Object function = getClientProperty(STATUS_VISIBLE_FUNCTION);
        if (function instanceof BooleanFunction) {
          //noinspection unchecked
          return ((BooleanFunction<JTextComponent>)function).fun(JBTextField.this);
        }
        return super.isStatusVisible();
      }

      @Override
      protected Rectangle getTextComponentBound() {
        return getEmptyTextComponentBounds(super.getTextComponentBound());
      }
    };
  }

  protected Rectangle getEmptyTextComponentBounds(Rectangle bounds) {
    return bounds;
  }

  public void setTextToTriggerEmptyTextStatus(String t) {
    myEmptyText.setTextToTriggerStatus(t);
  }

  @Override
  public void setText(String t) {
    if (Objects.equals(t, getText())) return;
    super.setText(t);
    UIUtil.resetUndoRedoActions(this);
  }

  @NotNull
  @Override
  public StatusText getEmptyText() {
    return myEmptyText;
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (!myEmptyText.getStatusTriggerText().isEmpty() && myEmptyText.isStatusVisible()) {
      g.setColor(getBackground());

      Rectangle rect = new Rectangle(getSize());
      JBInsets.removeFrom(rect, getInsets());
      JBInsets.removeFrom(rect, getMargin());
      ((Graphics2D)g).fill(rect);

      g.setColor(getForeground());
    }
    myEmptyText.paintStatusText(g);
  }

  @Override
  public String getToolTipText(MouseEvent event) {
    TextUI ui = getUI();
    @SuppressWarnings("HardCodedStringLiteral") String text = ui == null ? null : ui.getToolTipText(this, event.getPoint());
    return text != null ? text : getToolTipText();
  }
}
