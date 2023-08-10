// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options;

import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * SettingsEditor that could be extended. Extension should implement marker interface {@link ExtensionSettingsEditor}
 */
public class ExtendableSettingsEditor<T> extends SettingsEditor<T> {
  private final SettingsEditor<T> myMainEditor;
  private final List<SettingsEditor<T>> myExtensionEditors;

  public ExtendableSettingsEditor(SettingsEditor<T> mainEditor) {
    myMainEditor = mainEditor;
    Disposer.register(this, myMainEditor);
    myExtensionEditors = new ArrayList<>();
  }

  @Override
  protected void resetEditorFrom(@NotNull T s) {
    myMainEditor.resetFrom(s);
    for (SettingsEditor<T> extensionEditor : myExtensionEditors) {
      extensionEditor.resetFrom(s);
    }
  }

  @Override
  protected void applyEditorTo(@NotNull T s) throws ConfigurationException {
    myMainEditor.applyTo(s);
    for (SettingsEditor<T> extensionEditor : myExtensionEditors) {
      extensionEditor.applyTo(s);
    }
  }

  public void addExtensionEditor(@NotNull SettingsEditor<T> extensionSettingsEditor) {
    myExtensionEditors.add(extensionSettingsEditor);
    Disposer.register(this, extensionSettingsEditor);
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    JPanel result = new JPanel();
    result.setLayout(new GridBagLayout());

    JComponent mainEditorComponent = myMainEditor.getComponent();
    GridBagConstraints c = new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                                  JBInsets.emptyInsets(), 0, 0);
    result.add(mainEditorComponent, c);

    for (int i = 0; i < myExtensionEditors.size(); i++) {
      c = (GridBagConstraints)c.clone();
      c.gridy = i + 1;

      result.add(myExtensionEditors.get(i).getComponent(), c);
    }

    return result;
  }
}
