package com.intellij.openapi.options;

import com.intellij.ui.TabbedPaneWrapper;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public abstract class TabbedConfigurable extends CompositeConfigurable<Configurable> {
  protected TabbedPaneWrapper myTabbedPane;

  public JComponent createComponent() {
    myTabbedPane = new TabbedPaneWrapper();
    createConfigurableTabs();
    final JComponent component = myTabbedPane.getComponent();
    component.setPreferredSize(new Dimension(500, 400));
    return component;
  }

  protected void createConfigurableTabs() {
    for (Configurable configurable : getConfigurables()) {
      myTabbedPane.addTab(configurable.getDisplayName(), configurable.getIcon(), configurable.createComponent(), null);
    }
  }

  @Override
  public void disposeUIResources() {
    myTabbedPane = null;
    super.disposeUIResources();
  }
}
