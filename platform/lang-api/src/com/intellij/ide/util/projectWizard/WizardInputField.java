/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.options.ConfigurationException;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 *         Date: 2/1/13
 */
public abstract class WizardInputField<T extends JComponent> {

  public static final String IJ_BASE_PACKAGE = "IJ_BASE_PACKAGE";
  private final String myId;

  protected WizardInputField(String id) {
    myId = id;
  }

  public String getId() {
    return myId;
  }

  public abstract String getLabel();

  public abstract T getComponent();

  public abstract String getValue();

  public boolean validate() throws ConfigurationException { return true; }

  public static WizardInputField getFieldById(String id, String initialValue) {
    WizardInputFieldFactory[] extensions = WizardInputFieldFactory.EP_NAME.getExtensions();
    for (WizardInputFieldFactory extension : extensions) {
      WizardInputField field = extension.createField(id, initialValue);
      if (field != null) {
        return field;
      }
    }
    return null;
  }

  public void addToSettings(SettingsStep settingsStep) {
    settingsStep.addSettingsField(getLabel(), getComponent());
  }
}
