// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.util;

import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class SyncHeightComponent extends JPanel {
  @NotNull private final List<? extends JComponent> myComponents;

  SyncHeightComponent(@NotNull List<? extends JComponent> components, int index) {
    super(new BorderLayout());
    myComponents = components;
    JComponent delegate = components.get(index);
    if (delegate != null) add(delegate, BorderLayout.CENTER);
  }

  @Override
  public Dimension getMinimumSize() {
    Dimension size = super.getMinimumSize();
    size.height = getMaximumHeight(JComponent::getPreferredSize);
    return size;
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    size.height = getMaximumHeight(JComponent::getPreferredSize);
    return size;
  }

  private int getMaximumHeight(@NotNull Function<? super JComponent, ? extends Dimension> getter) {
    int height = 0;
    for (JComponent component : myComponents) {
      if (component != null) {
        height = Math.max(height, getter.fun(component).height);
      }
    }
    return height;
  }

  public void revalidateAll() {
    for (JComponent component : myComponents) {
      if (component != null) component.revalidate();
    }
  }
}
