package com.intellij.debugger.ui.tree.render;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;

import javax.swing.*;
import java.awt.*;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class CompoundNodeConfigurable implements UnnamedConfigurable {
  private final CompoundNodeRenderer myRenderer;

  private final UnnamedConfigurable myLabelConfigurable;
  private final UnnamedConfigurable myChildrenConfigurable;

  private final static UnnamedConfigurable NULL_CONFIGURABLE = new UnnamedConfigurable() {
    public JComponent createComponent() {
      return new JPanel();
    }

    public boolean isModified() {
      return false;
    }

    public void apply() {}
    public void reset() {}
    public void disposeUIResources() {}
  };

  public CompoundNodeConfigurable(CompoundNodeRenderer renderer,
                                  UnnamedConfigurable labelConfigurable,
                                  UnnamedConfigurable childrenConfigurable) {
    myRenderer = renderer;
    myLabelConfigurable    = labelConfigurable    != null ? labelConfigurable    : NULL_CONFIGURABLE;
    myChildrenConfigurable = childrenConfigurable != null ? childrenConfigurable : NULL_CONFIGURABLE;
  }

  public CompoundNodeRenderer getRenderer() {
    return myRenderer;
  }

  public JComponent createComponent() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.BOTH;
    c.weightx = 1.0;
    c.insets = new Insets(0, 0, 5, 0);
    c.gridwidth = GridBagConstraints.REMAINDER;

    panel.add(myLabelConfigurable.createComponent(), c);

    c.ipady = 1;
    panel.add(new JSeparator(JSeparator.HORIZONTAL), c);

    c.ipady = 0;
    c.weighty = 1.0;
    panel.add(myChildrenConfigurable.createComponent(), c);
    return panel;
  }

  public boolean isModified() {
    return myLabelConfigurable.isModified() || myChildrenConfigurable.isModified();
  }

  public void apply() throws ConfigurationException {
    myLabelConfigurable.apply();
    myChildrenConfigurable.apply();
  }

  public void reset() {
    myLabelConfigurable.reset();
    myChildrenConfigurable.reset();
  }

  public void disposeUIResources() {
    myLabelConfigurable.disposeUIResources();
    myChildrenConfigurable.disposeUIResources();
  }
}
