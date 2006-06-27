/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
import com.intellij.openapi.util.Disposer;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class SettingsEditorGroup<T> extends SettingsEditor<T> {
  private List<Pair<String, SettingsEditor<T>>> myEditors = new ArrayList<Pair<String, SettingsEditor<T>>>();

  public void addEditor(String name, SettingsEditor<T> editor) {
    Disposer.register(this, editor);
    myEditors.add(new Pair<String, SettingsEditor<T>>(name, editor));
  }

  public void addGroup(SettingsEditorGroup<T> group) {
    for (final Pair<String, SettingsEditor<T>> pair : group.myEditors) {
      Disposer.register(this, pair.second);
    }
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