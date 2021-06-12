// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.util;

import com.intellij.util.ui.AbstractLayoutManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class SyncHeightComponent extends JPanel {
  @NotNull private final List<? extends JComponent> myComponents;

  SyncHeightComponent(@NotNull List<? extends JComponent> syncComponents, @Nullable JComponent component) {
    super(new SyncHeightLayout(syncComponents, component));
    myComponents = syncComponents;

    if (component != null) add(component);
  }

  public void revalidateAll() {
    for (JComponent component : myComponents) {
      if (component != null) component.revalidate();
    }
  }

  private static class SyncHeightLayout extends AbstractLayoutManager {
    @NotNull private final List<? extends JComponent> mySyncComponents;
    @Nullable private final JComponent myComponent;

    SyncHeightLayout(@NotNull List<? extends JComponent> syncComponents, @Nullable JComponent component) {
      mySyncComponents = syncComponents;
      myComponent = component;
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      int totalHeight = 0;

      for (JComponent component : mySyncComponents) {
        Dimension size = getPreferredSize(component);
        totalHeight = Math.max(size.height, totalHeight);
      }

      int width = getPreferredSize(myComponent).width;

      return new Dimension(width, totalHeight);
    }

    @Override
    public void layoutContainer(@NotNull Container parent) {
      int width = parent.getWidth();
      int height = parent.getHeight();

      if (myComponent == null) return;

      Dimension size = getPreferredSize(myComponent);
      myComponent.setBounds(0, 0, width, Math.min(height, size.height));
    }

    @NotNull
    private static Dimension getPreferredSize(@Nullable Component component) {
      return component != null && component.isVisible() ? component.getPreferredSize() : new Dimension();
    }
  }
}
