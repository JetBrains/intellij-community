/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.debugger.ui.tree.render;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;

import javax.swing.*;
import java.awt.*;

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
