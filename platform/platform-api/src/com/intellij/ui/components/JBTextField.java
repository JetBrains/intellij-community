// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components;

import com.intellij.ui.TextAccessor;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.TextUI;
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

  @Override
  protected Graphics getComponentGraphics(Graphics graphics) {
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics));
  }

  private void init() {
    SwingUndoUtil.addUndoRedoActions(this);
    myEmptyText = new TextComponentEmptyText(this, true) {
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
    SwingUndoUtil.resetUndoRedoActions(this);
  }

  @Override
  public @NotNull StatusText getEmptyText() {
    return myEmptyText;
  }

  @Override
  @SuppressWarnings("DuplicatedCode")
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
    String text = ui == null ? null : ui.getToolTipText2D(this, event.getPoint());
    return text != null ? text : getToolTipText();
  }
}
