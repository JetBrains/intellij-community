package com.intellij.openapi.options;

import com.intellij.ui.TabbedPaneWrapper;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public abstract class TabbedConfigurable extends CompositeConfigurable<Configurable> {
  private TabbedPaneWrapper myTabbedPane;

  public JComponent createComponent() {
    myTabbedPane = new TabbedPaneWrapper();
    for (Configurable configurable : getConfigurables()) {
      myTabbedPane.addTab(configurable.getDisplayName(), configurable.getIcon(), configurable.createComponent(), null);
    }
    final JComponent component = myTabbedPane.getComponent();
    component.setPreferredSize(new Dimension(500, 400));
    return component;
  }

  @Override
  public void disposeUIResources() {
    myTabbedPane = null;
    super.disposeUIResources();
  }
}
