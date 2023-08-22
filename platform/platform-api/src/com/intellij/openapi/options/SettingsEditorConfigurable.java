// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class SettingsEditorConfigurable<Settings> extends BaseConfigurable {
  private SettingsEditor<Settings> myEditor;
  private final Settings mySettings;
  private final SettingsEditorListener<Settings> myListener;

  public SettingsEditorConfigurable(@NotNull SettingsEditor<Settings> editor, @NotNull Settings settings) {
    myEditor = editor;
    mySettings = settings;
    myListener = new SettingsEditorListener<>() {
      @Override
      public void stateChanged(@NotNull SettingsEditor<Settings> settingsEditor) {
        setModified(true);
      }
    };
    myEditor.addSettingsEditorListener(myListener);
  }

  @Override
  public JComponent createComponent() {
    return myEditor.getComponent();
  }

  @Override
  public void apply() throws ConfigurationException {
    myEditor.applyTo(mySettings);
    setModified(false);
  }

  @Override
  public void reset() {
    myEditor.resetFrom(mySettings);
    setModified(false);
  }

  @Override
  public void disposeUIResources() {
    if (myEditor != null) {
      myEditor.removeSettingsEditorListener(myListener);
      Disposer.dispose(myEditor);
    }
    myEditor = null;
  }

  @NotNull
  public SettingsEditor<Settings> getEditor() {
    // myEditor is null only if disposed
    return myEditor;
  }

  @NotNull
  public Settings getSettings() {
    return mySettings;
  }
}
