/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.options;

import com.intellij.openapi.util.Pair;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class SettingsEditorGroup<T> extends SettingsEditor<T> {
  private List<Pair<String, SettingsEditor<T>>> myEditors = new ArrayList<Pair<String, SettingsEditor<T>>>();

  public void addEditor(String name, SettingsEditor<T> editor) {
    myEditors.add(new Pair<String, SettingsEditor<T>>(name, editor));
  }

  public void addGroup(SettingsEditorGroup<T> group) {
    myEditors.addAll(group.myEditors);
  }

  public List<Pair<String, SettingsEditor<T>>> getEditors() {
    return myEditors;
  }

  public void resetEditorFrom(T t) {}
  public void applyEditorTo(T t) throws ConfigurationException {}

  public JComponent createEditor() {
    return null;
  }

  public void disposeEditor() {}
}