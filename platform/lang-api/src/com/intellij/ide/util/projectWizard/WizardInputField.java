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
import com.intellij.openapi.util.KeyedExtensionCollector;
import com.intellij.util.KeyedLazyInstanceEP;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 *         Date: 2/1/13
 */
public abstract class WizardInputField<T extends JComponent> {

  private static final String EP_NAME = "com.intellij.wizardInputField";

  public abstract String getLabel();

  public abstract T createComponent();

  public abstract String getValue(T component);

  public boolean validate() throws ConfigurationException { return true; }

  private static KeyedExtensionCollector<WizardInputField, String> COLLECTOR =
    new KeyedExtensionCollector<WizardInputField, String>(EP_NAME);

  public static WizardInputField getFieldById(String id) {
    return COLLECTOR.findSingle(id);
  }

  public void addToSettings(SettingsStep settingsStep) {
    settingsStep.addSettingsField(getLabel(), createComponent());
  }

  public static class Bean extends KeyedLazyInstanceEP<WizardInputField> {}
}
