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

package com.intellij.application.options.editor;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class EditorOptions implements SearchableConfigurable {
  @NonNls public static final String ID = "preferences.editor";
  private EditorOptionsPanel myEditorOptionsPanel;

  @Override
  public String getDisplayName() {
    return ApplicationBundle.message("title.editor");
  }

  @Override
  public String getHelpTopic() {
    return ID;
  }

  @Override
  @NotNull
  public String getId() {
    return ID;
  }

  @Override
  public Runnable enableSearch(final String option) {
    return null;
  }

  @Override
  public JComponent createComponent() {
    myEditorOptionsPanel = new EditorOptionsPanel();
    return myEditorOptionsPanel.getComponent();
  }

  @Override
  public boolean isModified() {
    return myEditorOptionsPanel != null && myEditorOptionsPanel.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myEditorOptionsPanel != null) {
      myEditorOptionsPanel.apply();
    }
  }

  @Override
  public void reset() {
    if (myEditorOptionsPanel != null) {
      myEditorOptionsPanel.reset();
    }
  }

  @Override
  public void disposeUIResources() {
    myEditorOptionsPanel = null;
  }
}
