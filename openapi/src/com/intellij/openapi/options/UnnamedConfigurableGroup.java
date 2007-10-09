/*
 * Copyright 2000-2007 JetBrains s.r.o.
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