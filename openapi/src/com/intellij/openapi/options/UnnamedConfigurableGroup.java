/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.options;

import com.intellij.openapi.ui.VerticalFlowLayout;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class UnnamedConfigurableGroup implements UnnamedConfigurable {
  private List<UnnamedConfigurable> myConfigurables = new ArrayList<UnnamedConfigurable>();

  public JComponent createComponent() {
    JPanel panel = new JPanel(new VerticalFlowLayout());
    for (int i = 0; i < myConfigurables.size(); i++) {
      UnnamedConfigurable configurable = myConfigurables.get(i);
      panel.add(configurable.createComponent());
    }

    return panel;
  }

  public boolean isModified() {
    for (int i = 0; i < myConfigurables.size(); i++) {
      if (myConfigurables.get(i).isModified()) return true;
    }
    return false;
  }

  public void apply() throws ConfigurationException {
    for (int i = 0; i < myConfigurables.size(); i++) {
      myConfigurables.get(i).apply();
    }
  }

  public void reset() {
    for (int i = 0; i < myConfigurables.size(); i++) {
      myConfigurables.get(i).reset();
    }
  }

  public void disposeUIResources() {
    for (int i = 0; i < myConfigurables.size(); i++) {
      myConfigurables.get(i).disposeUIResources();
    }
  }

  public void add(UnnamedConfigurable configurable) {
    myConfigurables.add(configurable);
  }
}