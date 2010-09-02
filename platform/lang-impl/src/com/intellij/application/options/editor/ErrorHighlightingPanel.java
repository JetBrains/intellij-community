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

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.openapi.options.AbstractConfigurableEP;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.profile.codeInspection.ui.ErrorOptionsProvider;
import com.intellij.profile.codeInspection.ui.ErrorOptionsProviderEP;

import javax.swing.*;
import java.util.List;

public class ErrorHighlightingPanel {
  private JTextField myAutoreparseDelayField;

  private JTextField myMarkMinHeight;
  private JPanel myPanel;
  private JPanel myErrorsPanel;
  private JCheckBox myNextErrorGoesToErrorsFirst;
  private final List<ErrorOptionsProvider> myExtensions;

  public ErrorHighlightingPanel() {
    myExtensions = AbstractConfigurableEP.createConfigurables(ErrorOptionsProviderEP.EP_NAME);
    for (ErrorOptionsProvider optionsProvider : myExtensions) {
      myErrorsPanel.add(optionsProvider.createComponent());
    }
  }

  public void reset() {
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
    myAutoreparseDelayField.setText(Integer.toString(settings.AUTOREPARSE_DELAY));

    myMarkMinHeight.setText(Integer.toString(settings.ERROR_STRIPE_MARK_MIN_HEIGHT));
    myNextErrorGoesToErrorsFirst.setSelected(settings.NEXT_ERROR_ACTION_GOES_TO_ERRORS_FIRST);

    for (ErrorOptionsProvider optionsProvider : myExtensions) {
      optionsProvider.reset();
    }
  }

  public void apply() throws ConfigurationException {
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();

    settings.AUTOREPARSE_DELAY = getAutoReparseDelay();

    settings.ERROR_STRIPE_MARK_MIN_HEIGHT = getErrorStripeMarkMinHeight();

    settings.NEXT_ERROR_ACTION_GOES_TO_ERRORS_FIRST = myNextErrorGoesToErrorsFirst.isSelected();


    for (ErrorOptionsProvider optionsProvider : myExtensions) {
      optionsProvider.apply();
    }
  }

  public JPanel getPanel(){
    return myPanel;
  }

  private int getErrorStripeMarkMinHeight() {
    return parseInteger(myMarkMinHeight);
  }

  public boolean isModified() {
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
    boolean isModified = settings.AUTOREPARSE_DELAY != getAutoReparseDelay();
    
    isModified |= getErrorStripeMarkMinHeight() != settings.ERROR_STRIPE_MARK_MIN_HEIGHT;
    isModified |= myNextErrorGoesToErrorsFirst.isSelected() != settings.NEXT_ERROR_ACTION_GOES_TO_ERRORS_FIRST;
    for (ErrorOptionsProvider optionsProvider : myExtensions) {
      isModified |= optionsProvider.isModified();
    }
    if (isModified) return true;
    return false;
  }


  public void disposeUIResources() {
    for (ErrorOptionsProvider optionsProvider : myExtensions) {
      optionsProvider.disposeUIResources();
    }
  }

  private int getAutoReparseDelay() {
    return parseInteger(myAutoreparseDelayField);
  }

  private static int parseInteger(final JTextField textField) {
    try {
      int delay = Integer.parseInt(textField.getText());
      if (delay < 0) {
        delay = 0;
      }
      return delay;
    }
    catch (NumberFormatException e) {
      return 0;
    }
  }
}
