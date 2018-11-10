// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.application.options.editor;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
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
    myExtensions = ConfigurableWrapper.createConfigurables(ErrorOptionsProviderEP.EP_NAME);
    for (ErrorOptionsProvider optionsProvider : myExtensions) {
      myErrorsPanel.add(optionsProvider.createComponent());
    }
  }

  public void reset() {
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
    myAutoreparseDelayField.setText(Integer.toString(settings.getAutoReparseDelay()));

    myMarkMinHeight.setText(Integer.toString(settings.getErrorStripeMarkMinHeight()));
    myNextErrorGoesToErrorsFirst.setSelected(settings.isNextErrorActionGoesToErrorsFirst());

    for (ErrorOptionsProvider optionsProvider : myExtensions) {
      optionsProvider.reset();
    }
  }

  public void apply() throws ConfigurationException {
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();

    settings.setAutoReparseDelay(getAutoReparseDelay());

    settings.setErrorStripeMarkMinHeight(getErrorStripeMarkMinHeight());

    settings.setNextErrorActionGoesToErrorsFirst(myNextErrorGoesToErrorsFirst.isSelected());


    for (ErrorOptionsProvider optionsProvider : myExtensions) {
      optionsProvider.apply();
    }
    UISettings.getInstance().fireUISettingsChanged();
  }

  public JPanel getPanel(){
    return myPanel;
  }

  private int getErrorStripeMarkMinHeight() {
    return parseInteger(myMarkMinHeight);
  }

  public boolean isModified() {
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
    boolean isModified = settings.getAutoReparseDelay() != getAutoReparseDelay();

    isModified |= getErrorStripeMarkMinHeight() != settings.getErrorStripeMarkMinHeight();
    isModified |= myNextErrorGoesToErrorsFirst.isSelected() != settings.isNextErrorActionGoesToErrorsFirst();
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
