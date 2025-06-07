// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options;

import com.intellij.openapi.util.NlsContexts.TabTitle;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBTabbedPane;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class GroupSettingsBuilder<T> implements CompositeSettingsBuilder<T> {
  private final SettingsEditorGroup<T> myGroup;
  private JComponent myComponent;

  public GroupSettingsBuilder(SettingsEditorGroup<T> group) {
    myGroup = group;
  }

  @Override
  public @NotNull Collection<SettingsEditor<T>> getEditors() {
    List<SettingsEditor<T>> result = new ArrayList<>();
    List<Pair<String,SettingsEditor<T>>> editors = myGroup.getEditors();
    for (int i = 0; i < editors.size(); i++) {
      result.add(editors.get(i).getSecond());
    }
    return result;
  }

  @Override
  public @NotNull JComponent createCompoundEditor() {
    if (myComponent == null) {
      myComponent = doCreateComponent();
    }
    return myComponent;
  }

  private JComponent doCreateComponent() {
    List<Pair<String,SettingsEditor<T>>> editors = myGroup.getEditors();
    if (editors.isEmpty()) return new JPanel();
    if (editors.size() == 1) return editors.get(0).getSecond().getComponent();

    JTabbedPane tabs = new JBTabbedPane();
    for (int i = 0; i < editors.size(); i++) {
      Pair<@TabTitle String, SettingsEditor<T>> pair = editors.get(i);
      tabs.add(pair.getFirst(), pair.getSecond().getComponent());
    }

    tabs.putClientProperty("JTabbedPane.hasFullBorder", Boolean.TRUE);
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