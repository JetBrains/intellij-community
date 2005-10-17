package com.intellij.debugger.settings;

import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.TabbedPaneWrapper;

import javax.swing.*;
import java.util.Iterator;
import java.util.List;
import java.awt.*;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public abstract class CompositeConfigurable extends BaseConfigurable {
  private List<Configurable> myConfigurables;
  private TabbedPaneWrapper myTabbedPane;

  public void reset() {
    for (Iterator<Configurable> iterator = getConfigurables().iterator(); iterator.hasNext();) {
      Configurable configurable = iterator.next();
      configurable.reset();
    }
  }

  public void apply() throws ConfigurationException {
    for (Iterator<Configurable> iterator = getConfigurables().iterator(); iterator.hasNext();) {
      Configurable configurable = iterator.next();
      configurable.apply();
    }
  }

  public boolean isModified() {
    for (Iterator<Configurable> iterator = getConfigurables().iterator(); iterator.hasNext();) {
      Configurable configurable = iterator.next();
      if(configurable.isModified()) {
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
      for (Iterator<Configurable> it = myConfigurables.iterator(); it.hasNext();) {
        it.next().disposeUIResources();
      }
      myConfigurables = null;
    }
  }

  protected abstract List<Configurable> createConfigurables();

  private List<Configurable> getConfigurables() {
    if (myConfigurables == null) {
      myConfigurables = createConfigurables();
    }
    return myConfigurables;
  }
}
