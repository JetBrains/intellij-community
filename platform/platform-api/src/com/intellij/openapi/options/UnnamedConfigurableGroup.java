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
package com.intellij.openapi.options;

import com.intellij.openapi.ui.VerticalFlowLayout;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class UnnamedConfigurableGroup implements UnnamedConfigurable {
  private final List<UnnamedConfigurable> myConfigurables = new ArrayList<UnnamedConfigurable>();

  public JComponent createComponent() {
    JPanel panel = new JPanel(new VerticalFlowLayout());
    for (UnnamedConfigurable configurable : myConfigurables) {
      panel.add(configurable.createComponent());
    }

    return panel;
  }

  public boolean isModified() {
    for (UnnamedConfigurable myConfigurable : myConfigurables) {
      if (myConfigurable.isModified()) return true;
    }
    return false;
  }

  public void apply() throws ConfigurationException {
    for (UnnamedConfigurable myConfigurable : myConfigurables) {
      myConfigurable.apply();
    }
  }

  public void reset() {
    for (UnnamedConfigurable myConfigurable : myConfigurables) {
      myConfigurable.reset();
    }
  }

  public void disposeUIResources() {
    for (UnnamedConfigurable myConfigurable : myConfigurables) {
      myConfigurable.disposeUIResources();
    }
  }

  public void add(UnnamedConfigurable configurable) {
    myConfigurables.add(configurable);
  }
}
