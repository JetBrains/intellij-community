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
import com.intellij.openapi.options.AbstractConfigurableEP;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.List;

public class EditorOptions implements SearchableConfigurable.Parent {
  @NonNls public static final String ID = "preferences.editor";
  private EditorOptionsPanel myEditorOptionsPanel;
  private Configurable[] myChildren;

  public Configurable[] getConfigurables() {
    if (myChildren == null) {
      final List<EditorOptionsProvider> configurables = AbstractConfigurableEP.createConfigurables(EditorOptionsProviderEP.EP_NAME);
      myChildren = configurables.toArray(new EditorOptionsProvider[configurables.size()]);
    }
    return myChildren;
  }

  public String getDisplayName() {
    return ApplicationBundle.message("title.editor");
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableEditor.png");
  }

  public String getHelpTopic() {
    return ID;
  }

  public String getId() {
    return ID;
  }

  public Runnable enableSearch(final String option) {
    return null;
  }

  public boolean hasOwnContent() {
    return true;
  }

  public boolean isVisible() {
    return true;
  }

  public JComponent createComponent() {
    myEditorOptionsPanel = new EditorOptionsPanel();
    return myEditorOptionsPanel.getComponent();
  }

  public boolean isModified() {
    return myEditorOptionsPanel != null && myEditorOptionsPanel.isModified();
  }

  public void apply() throws ConfigurationException {
    if (myEditorOptionsPanel != null) {
      myEditorOptionsPanel.apply();
    }
  }

  public void reset() {
    if (myEditorOptionsPanel != null) {
      myEditorOptionsPanel.reset();
    }
  }

  public void disposeUIResources() {
    myEditorOptionsPanel = null;

  }
}