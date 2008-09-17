package com.intellij.openapi.options;

import com.intellij.ui.TabbedPaneWrapper;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public abstract class CompositeConfigurable extends BaseConfigurable {
  private List<Configurable> myConfigurables;
  private TabbedPaneWrapper myTabbedPane;

  public void reset() {
    for (Configurable configurable : getConfigurables()) {
      configurable.reset();
    }
  }

  public void apply() throws ConfigurationException {
    for (Configurable configurable : getConfigurables()) {
      configurable.apply();
    }
  }

  public boolean isModified() {
    for (Configurable configurable : getConfigurables()) {
      if (configurable.isModified()) {
        return true;
      }
    }
    return false;
  }

  public JComponent createComponent() {
    myTabbedPane = new TabbedPaneWrapper();
    for (Configurable configurable : getConfigurables()) {
      myTabbedPane.addTab(configurable.getDisplayName(), configurable.getIcon(), configurable.createComponent(), null);
    }
    final JComponent component = myTabbedPane.getComponent();
    component.setPreferredSize(new Dimension(500, 400));
    return component;
  }

  public void disposeUIResources() {
    myTabbedPane = null;
    if (myConfigurables != null) {
      for (final Configurable myConfigurable : myConfigurables) {
        myConfigurable.disposeUIResources();
      }
      myConfigurables = null;
    }
  }

  protected abstract List<Configurable> createConfigurables();

  public List<Configurable> getConfigurables() {
    if (myConfigurables == null) {
      myConfigurables = createConfigurables();
    }
    return myConfigurables;
  }
}
