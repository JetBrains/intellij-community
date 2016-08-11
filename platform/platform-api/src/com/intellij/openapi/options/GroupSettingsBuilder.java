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

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBTabbedPane;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class GroupSettingsBuilder<T> implements CompositeSettingsBuilder<T> {
  private final SettingsEditorGroup<T> myGroup;
  private JComponent myComponent;

  public GroupSettingsBuilder(SettingsEditorGroup<T> group) {
    myGroup = group;
  }

  public Collection<SettingsEditor<T>> getEditors() {
    List<SettingsEditor<T>> result = new ArrayList<>();
    List<Pair<String,SettingsEditor<T>>> editors = myGroup.getEditors();
    for (int i = 0; i < editors.size(); i++) {
      result.add(editors.get(i).getSecond());
    }
    return result;
  }

  public JComponent createCompoundEditor() {
    if (myComponent == null) {
      myComponent = doCreateComponent();
    }
    return myComponent;
  }

  private JComponent doCreateComponent() {
    List<Pair<String,SettingsEditor<T>>> editors = myGroup.getEditors();
    if (editors.size() == 0) return new JPanel();
    if (editors.size() == 1) return editors.get(0).getSecond().getComponent();

    JTabbedPane tabs = new JBTabbedPane();
    for (int i = 0; i < editors.size(); i++) {
      Pair<String, SettingsEditor<T>> pair = editors.get(i);
      JPanel panel = new JPanel(new BorderLayout());
      panel.add(pair.getSecond().getComponent(), BorderLayout.CENTER);
      tabs.add(pair.getFirst(), panel);
    }

    return tabs;
  }

  public void selectEditor(String tabName) {
    List<Pair<String,SettingsEditor<T>>> editors = myGroup.getEditors();
    if (myComponent != null && editors.size() > 1) {
      for (int i = 0; i < editors.size(); i++) {
        Pair<String, SettingsEditor<T>> pair = editors.get(i);
        if (StringUtil.equals(tabName, pair.getFirst())) {
          ((JTabbedPane)myComponent).setSelectedIndex(i);
          return;
        }
      }
    }
  }
}