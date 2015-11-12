/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.tools.util;

import com.intellij.diff.tools.util.DiffSplitter.Painter;
import com.intellij.diff.util.Side;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class ThreeDiffSplitter extends JPanel {
  private static final int DIVIDER_WIDTH = 30;

  @NotNull private final List<Divider> myDividers;
  @NotNull private final List<? extends JComponent> myContents;

  public ThreeDiffSplitter(@NotNull List<? extends JComponent> components) {
    myDividers = ContainerUtil.list(new Divider(), new Divider());
    myContents = components;

    addAll(myContents);
    addAll(myDividers);
  }

  @CalledInAwt
  public void setPainter(@Nullable Painter painter, @NotNull Side side) {
    getDivider(side).setPainter(painter);
  }

  public void repaintDividers() {
    repaintDivider(Side.LEFT);
    repaintDivider(Side.RIGHT);
  }

  public void repaintDivider(@NotNull Side side) {
    getDivider(side).repaint();
  }

  @NotNull
  private Divider getDivider(@NotNull Side side) {
    return myDividers.get(side.getIndex());
  }

  private void addAll(@NotNull List<? extends JComponent> components) {
    for (JComponent component : components) {
      add(component, -1);
    }
  }

  @Override
  public void doLayout() {
    int width = getWidth();
    int height = getHeight();
    int panelWidth = (width - DIVIDER_WIDTH * 2) / 3;
    int x = 0;
    for (int i = 0; i < myContents.size(); i++) {
      JComponent component = myContents.get(i);
      component.setBounds(x, 0, panelWidth, height);
      component.validate();
      x += panelWidth;
      if (i < myDividers.size()) {
        JComponent divider = myDividers.get(i);
        divider.setBounds(x, 0, DIVIDER_WIDTH, height);
        divider.validate();
        x += DIVIDER_WIDTH;
      }
    }
  }

  @Override
  public Dimension getMinimumSize() {
    int width = DIVIDER_WIDTH * 2;
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
    int width = DIVIDER_WIDTH * 2;
    int height = 0;
    for (JComponent content : myContents) {
      Dimension size = content.getPreferredSize();
      width += size.width;
      height = Math.max(height, size.height);
    }
    return new Dimension(width, height);
  }

  private static class Divider extends JComponent {
    @Nullable private Painter myPainter;

    @Override
    public void paint(Graphics g) {
      super.paint(g);
      if (myPainter != null) myPainter.paint(g, this);
    }

    @CalledInAwt
    public void setPainter(@Nullable Painter painter) {
      myPainter = painter;
    }
  }
}
