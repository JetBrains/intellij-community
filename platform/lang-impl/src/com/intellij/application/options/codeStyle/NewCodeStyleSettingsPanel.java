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

package com.intellij.application.options.codeStyle;

import com.intellij.application.options.CodeStyleAbstractConfigurable;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author max
 */
public class NewCodeStyleSettingsPanel extends JPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.application.options.codeStyle.NewCodeStyleSettingsPanel");

  private final Configurable myTab;

  public NewCodeStyleSettingsPanel(Configurable tab) {
    super(new BorderLayout());
    myTab = tab;
    JComponent component = myTab.createComponent();
    add(component, BorderLayout.CENTER);

  }

  public boolean isModified() {
    return myTab.isModified();
  }

  public void updatePreview() {
    if (myTab instanceof CodeStyleAbstractConfigurable) {
      ((CodeStyleAbstractConfigurable)myTab).getPanel().onSomethingChanged();
    }
  }

  public void apply() {
    try {
      if (myTab.isModified()) {
        myTab.apply();
      }
    }
    catch (ConfigurationException e) {
      LOG.error(e);
    }
  }

  @Nullable
  public String getHelpTopic() {
    return myTab.getHelpTopic();
  }

  public void dispose() {
    myTab.disposeUIResources();
  }

  public void reset() {
    myTab.reset();
    updatePreview();
  }

  public String getDisplayName() {
    return myTab.getDisplayName();
  }

  public void setModel(final CodeStyleSchemesModel model) {
    if (myTab instanceof CodeStyleAbstractConfigurable) {
      ((CodeStyleAbstractConfigurable)myTab).setModel(model);
    }
  }

  public void onSomethingChanged() {
    if (myTab instanceof CodeStyleAbstractConfigurable) {
      ((CodeStyleAbstractConfigurable)myTab).onSomethingChanged();
    }
  }

  public void resetFromClone() {
    if (myTab instanceof CodeStyleAbstractConfigurable) {
      ((CodeStyleAbstractConfigurable)myTab).resetFromClone();
    }


  }

  public boolean isMultilanguage() {
    if (myTab instanceof CodeStyleAbstractConfigurable) {
      return ((CodeStyleAbstractConfigurable)myTab).isMultilanguage();
    }
    return false;
  }

  public void setLanguage(Language language) {
    if (myTab instanceof CodeStyleAbstractConfigurable) {
      CodeStyleAbstractConfigurable configurable = (CodeStyleAbstractConfigurable)myTab;
      if (configurable.isMultilanguage()) {
        if (configurable.getPanel() instanceof MultilanguageCodeStyleAbstractPanel) {
          ((MultilanguageCodeStyleAbstractPanel)configurable.getPanel()).setLanguage(language);          
        }
      }
    }
  }
}
