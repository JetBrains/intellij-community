/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.options;

import javax.swing.*;

public abstract class SettingsEditorConfigurable<Settings> extends BaseConfigurable {
  private SettingsEditor<Settings> myEditor;
  private Settings mySettings;
  private SettingsEditorListener<Settings> myListener;
  private JComponent myComponent;

  public SettingsEditorConfigurable(SettingsEditor<Settings> editor, Settings settings) {
    myEditor = editor;
    mySettings = settings;
    myListener = new SettingsEditorListener<Settings>() {
      public void stateChanged(SettingsEditor<Settings> settingsEditor) {
        setModified(true);
      }
    };
    myEditor.addSettingsEditorListener(myListener);
    myComponent = myEditor.getComponent();
  }

  public JComponent createComponent() {
    return myComponent;
  }

  public void apply() throws ConfigurationException {
    myEditor.applyTo(mySettings);
    setModified(false);
  }

  public void reset() {
    myEditor.resetFrom(mySettings);
    setModified(false);
  }

  public void disposeUIResources() {
    myEditor.removeSettingsEditorListener(myListener);
    myEditor.dispose();
    myEditor = null;
  }

  public SettingsEditor<Settings> getEditor() {
    return myEditor;
  }

  public Settings getSettings() {
    return mySettings;
  }
}