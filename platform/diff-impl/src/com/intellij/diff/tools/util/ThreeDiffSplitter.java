// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.util;

import com.intellij.diff.tools.util.DiffSplitter.Painter;
import com.intellij.diff.util.Side;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.MathUtil;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;

public class ThreeDiffSplitter extends JPanel {
  @NotNull private final List<? extends JComponent> myContents;
  @NotNull private final Divider myDivider1;
  @NotNull private final Divider myDivider2;

  private float myProportion1; // first size divided by (first + second + third)
  private float myProportion2; // third size divided by (first + second + third)

  public ThreeDiffSplitter(@NotNull List<? extends JComponent> components) {
    assert components.size() == 3;
    myContents = components;
    myDivider1 = new Divider(Side.LEFT);
    myDivider2 = new Divider(Side.RIGHT);

    add(myDivider1);
    add(myDivider2);
    for (JComponent content : myContents) {
      add(content);
    }

    resetProportions();
  }

  @RequiresEdt
  public void setPainter(@Nullable Painter painter, @NotNull Side side) {
    getDivider(side).setPainter(painter);
  }

  public void repaintDivider(@NotNull Side side) {
    getDivider(side).repaint();
  }

  @NotNull
  private Divider getDivider(@NotNull Side side) {
    return side.select(myDivider1, myDivider2);
  }

  private void resetProportions() {
    myProportion1 = myProportion2 = 1f / 3;
  }

  private void expandMiddlePanel() {
    myProportion1 = myProportion2 = 0f;
  }

  private boolean areDefaultProportions() {
    int width = getWidth();
    int[] widths1 = calcComponentsWidths(width, myProportion1, myProportion2);
    int[] widths2 = calcComponentsWidths(width, 1f / 3, 1f / 3);
    return Arrays.equals(widths1, widths2);
  }

  private void setProportion(float proportion, @NotNull Side side) {
    proportion = MathUtil.clamp(proportion, 0f, 1f);
    float otherProportion = side.select(myProportion2, myProportion1);
    otherProportion = Math.min(otherProportion, 1f - proportion);

    myProportion1 = side.select(proportion, otherProportion);
    myProportion2 = side.select(otherProportion, proportion);
  }

  @Override
  public void doLayout() {
    int width = getWidth();
    int height = getHeight();

    JComponent[] components = new JComponent[]{myContents.get(0), myDivider1, myContents.get(1), myDivider2, myContents.get(2)};
    int[] contentWidths = calcComponentsWidths(width, myProportion1, myProportion2);

    int x = 0;
    for (int i = 0; i < 5; i++) {
      JComponent component = components[i];
      component.setBounds(x, 0, contentWidths[i], height);
      component.validate();
      x += contentWidths[i];
    }
  }

  private static int @NotNull [] calcComponentsWidths(int width, float proportion1, float proportion2) {
    int dividersTotalWidth = getDividerWidth() * 2;
    int contentsTotalWidth = Math.max(width - dividersTotalWidth, 0);

    int[] contentWidths = new int[5];
    contentWidths[1] = getDividerWidth(); // divider1
    contentWidths[3] = getDividerWidth(); // divider2
    contentWidths[0] = (int)(contentsTotalWidth * proportion1); // content1
    contentWidths[4] = (int)(contentsTotalWidth * proportion2); // content3
    contentWidths[2] = Math.max(contentsTotalWidth - contentWidths[0] - contentWidths[4], 0); // content2
    return contentWidths;
  }

  @Override
  public Dimension getMinimumSize() {
    int width = getDividerWidth() * 2;
    int height = 0;
    for (JComponent content : myContents) {
      Dimension size = content.getMinimumSize();
      width += size.width;
      height = Math.max(height, size.height);
    }
    return new Dimension(width, height);
  }

  @Override
  public Dimension getPreferredSize() {
    int width = getDividerWidth() * 2;
    int height = 0;
    for (JComponent content : myContents) {
      Dimension size = content.getPreferredSize();
      width += size.width;
      height = Math.max(height, size.height);
    }
    return new Dimension(width, height);
  }

  private static int getDividerWidth() {
    return JBUIScale.scale(Registry.intValue("diff.divider.width"));
  }

  private class Divider extends JPanel {
    @NotNull private final Side mySide;
    @Nullable private Painter myPainter;

    Divider(@NotNull Side side) {
      mySide = side;
      enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
      setCursor(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (myPainter != null) myPainter.paint(g, this);
    }

    @RequiresEdt
    public void setPainter(@Nullable Painter painter) {
      myPainter = painter;
    }

    @Override
    protected void processMouseMotionEvent(MouseEvent e) {
      super.processMouseMotionEvent(e);
      if (e.getID() == MouseEvent.MOUSE_DRAGGED) {
        int totalWidth = ThreeDiffSplitter.this.getWidth();
        if (totalWidth > 0) {
          Point point = SwingUtilities.convertPoint(this, e.getPoint(), ThreeDiffSplitter.this);
          float proportion = (float)mySide.select(point.x, totalWidth - point.x) / (float)totalWidth;
          setProportion(proportion, mySide);

          revalidate();
          repaint();
        }
      }
    }

    @Override
    protected void processMouseEvent(MouseEvent e) {
      super.processMouseEvent(e);
      if (e.getID() == MouseEvent.MOUSE_CLICKED && e.getClickCount() == 2) {
        if (areDefaultProportions()) {
          expandMiddlePanel();
        }
        else {
          resetProportions();
        }

        revalidate();
        repaint();
      }
    }
  }
}
