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

import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.TextUI;
import java.awt.*;
import java.awt.event.MouseEvent;

public class JBTextField extends JTextField implements ComponentWithEmptyText {
  private TextComponentEmptyText myEmptyText;

  public JBTextField() {
    init();
  }

  public JBTextField(int columns) {
    super(columns);
    init();
  }

  public JBTextField(String text) {
    super(text);
    init();
  }

  public JBTextField(String text, int columns) {
    super(text, columns);
    init();
  }

  private void init() {
    UIUtil.addUndoRedoActions(this);
    myEmptyText = new TextComponentEmptyText(this);
  }

  public void setTextToTriggerEmptyTextStatus(String t) {
    myEmptyText.setTextToTriggerStatus(t);
  }

  @Override
  public void setText(String t) {
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
      g.fillRect(0, 0, getWidth(), getHeight());
      g.setColor(getForeground());
    }
    myEmptyText.paintStatusText(g);
  }

  @Override
  public String getToolTipText(MouseEvent event) {
    TextUI ui = getUI();
    String text = ui == null ? null : ui.getToolTipText(this, event.getPoint());
    return text != null ? text : getToolTipText();
  }
}
