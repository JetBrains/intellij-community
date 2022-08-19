// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.actions;

import com.intellij.ui.ActiveComponent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class CompositeActiveComponent implements ActiveComponent {
  private final ActiveComponent[] myComponents;
  private final JPanel myComponent;

  public CompositeActiveComponent(ActiveComponent @NotNull ... components) {
    myComponents = components;
    myComponent = CompositeActiveComponentPanelKt.createPanel(components);
  }

  @Override
  public void setActive(boolean active) {
    for (ActiveComponent component : myComponents) {
      component.setActive(active);
    }
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }
}
