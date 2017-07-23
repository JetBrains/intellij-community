/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.util.Disposer;

import javax.swing.*;

public abstract class SettingsEditorConfigurable<Settings> extends BaseConfigurable {
  private SettingsEditor<Settings> myEditor;
  private final Settings mySettings;
  private final SettingsEditorListener<Settings> myListener;
  private final JComponent myComponent;

  public SettingsEditorConfigurable(SettingsEditor<Settings> editor, Settings settings) {
    myEditor = editor;
    mySettings = settings;
    myListener = new SettingsEditorListener<Settings>() {
      @Override
      public void stateChanged(SettingsEditor<Settings> settingsEditor) {
        setModified(true);
      }
    };
    myEditor.addSettingsEditorListener(myListener);
    myComponent = myEditor.getComponent();
  }

  @Override
  public JComponent createComponent() {
    return myComponent;
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

  public SettingsEditor<Settings> getEditor() {
    return myEditor;
  }

  public Settings getSettings() {
    return mySettings;
  }
}
