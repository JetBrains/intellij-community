// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options;

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts.TabTitle;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class SettingsEditorGroup<T> extends SettingsEditor<T> {
  private final List<Pair<@TabTitle String, SettingsEditor<T>>> myEditors = new ArrayList<>();

  public void addEditor(@TabTitle String name, SettingsEditor<T> editor) {
    Disposer.register(this, editor);
    myEditors.add(Pair.create(name, editor));
  }

  public void addGroup(SettingsEditorGroup<T> group) {
    for (final Pair<String, SettingsEditor<T>> pair : group.myEditors) {
      Disposer.register(this, pair.second);
    }
    myEditors.addAll(group.myEditors);
  }

  public List<Pair<@TabTitle String, SettingsEditor<T>>> getEditors() {
    return myEditors;
  }

  @Override
  public void resetEditorFrom(@NotNull T t) {}
  @Override
  public void applyEditorTo(@NotNull T t) throws ConfigurationException {}

  @Override
  public @NotNull JComponent createEditor() {
    throw new UnsupportedOperationException("This method should never be called!");
  }
}
