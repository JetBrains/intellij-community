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
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.io.File;
import java.util.Collections;
import java.util.Map;

/**
 * @see ProjectTemplateParameterFactory
 * @author Dmitry Avdeev
 */
public abstract class WizardInputField<T extends JComponent> {

  private final String myId;
  private final String myDefaultValue;

  protected WizardInputField(String id, String defaultValue) {
    myId = id;
    myDefaultValue = defaultValue;
  }

  public String getId() {
    return myId;
  }

  public String getDefaultValue() {
    return myDefaultValue;
  }

  public abstract String getLabel();

  public abstract T getComponent();

  public abstract String getValue();

  public Map<String, String> getValues() {
    return Collections.singletonMap(getId(), getValue());
  }

  public boolean validate() throws ConfigurationException { return true; }

  public static ProjectTemplateParameterFactory getFactoryById(String id) {
    ProjectTemplateParameterFactory[] extensions = ProjectTemplateParameterFactory.EP_NAME.getExtensions();
    for (ProjectTemplateParameterFactory extension : extensions) {
      if (extension.getParameterId().equals(id)) {
        return extension;
      }
    }
    return null;
  }

  public void addToSettings(SettingsStep settingsStep) {
    settingsStep.addSettingsField(getLabel(), getComponent());
  }

  public boolean acceptFile(File file) {
    return true;
  }

  @TestOnly
  public void setValue(String value) {
    throw new UnsupportedOperationException();
  }
}
