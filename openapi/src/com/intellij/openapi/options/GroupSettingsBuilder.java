/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.options;

import com.intellij.openapi.util.Pair;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.awt.*;

public class GroupSettingsBuilder<T> implements CompositeSettingsBuilder<T> {
  private final SettingsEditorGroup<T> myGroup;
  private JComponent myComponent;

  public GroupSettingsBuilder(SettingsEditorGroup<T> group) {
    myGroup = group;
  }

  public Collection<SettingsEditor<T>> getEditors() {
    List<SettingsEditor<T>> result = new ArrayList<SettingsEditor<T>>();
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

    JTabbedPane tabs = new JTabbedPane();
    for (int i = 0; i < editors.size(); i++) {
      Pair<String, SettingsEditor<T>> pair = editors.get(i);
      JPanel panel = new JPanel(new BorderLayout());
      panel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
      panel.add(pair.getSecond().getComponent(), BorderLayout.CENTER);
      tabs.add(pair.getFirst(), panel);
    }

    return tabs;
  }
}