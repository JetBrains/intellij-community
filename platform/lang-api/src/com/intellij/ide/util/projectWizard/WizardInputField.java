// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

  public boolean validate() throws ConfigurationException {
    return true;
  }

  @Nullable
  public static ProjectTemplateParameterFactory getFactoryById(@NotNull String id) {
    for (ProjectTemplateParameterFactory extension : ProjectTemplateParameterFactory.EP_NAME.getExtensionList()) {
      if (extension.getParameterId().equals(id)) {
        return extension;
      }
    }
    return null;
  }

  public void addToSettings(SettingsStep settingsStep) {
    T component = getComponent();
    if (component != null) {
      settingsStep.addSettingsField(getLabel(), component);
    }
  }

  public boolean acceptFile(File file) {
    return true;
  }

  @TestOnly
  public void setValue(String value) {
    throw new UnsupportedOperationException();
  }
}
