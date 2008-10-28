package com.intellij.application.options.editor;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.profile.codeInspection.ui.ErrorOptionsProvider;

import javax.swing.*;

public class ErrorHighlightingPanel {
  private JTextField myAutoreparseDelayField;

  private JTextField myMarkMinHeight;
  private JPanel myPanel;
  private JPanel myErrorsPanel;
  private JCheckBox myNextErrorGoesToErrorsFirst;

  public ErrorHighlightingPanel() {
    for (ErrorOptionsProvider optionsProvider : Extensions.getExtensions(ErrorOptionsProvider.EP_NAME)) {
      myErrorsPanel.add(optionsProvider.createComponent());
    }
  }

  public void reset() {
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
    myAutoreparseDelayField.setText(Integer.toString(settings.AUTOREPARSE_DELAY));

    myMarkMinHeight.setText(Integer.toString(settings.ERROR_STRIPE_MARK_MIN_HEIGHT));
    myNextErrorGoesToErrorsFirst.setSelected(settings.NEXT_ERROR_ACTION_GOES_TO_ERRORS_FIRST);

    for (ErrorOptionsProvider optionsProvider : Extensions.getExtensions(ErrorOptionsProvider.EP_NAME)) {
      optionsProvider.reset();
    }
  }

  public void apply() throws ConfigurationException {
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();

    settings.AUTOREPARSE_DELAY = getAutoReparseDelay();

    settings.ERROR_STRIPE_MARK_MIN_HEIGHT = getErrorStripeMarkMinHeight();

    settings.NEXT_ERROR_ACTION_GOES_TO_ERRORS_FIRST = myNextErrorGoesToErrorsFirst.isSelected();


    for (ErrorOptionsProvider optionsProvider : Extensions.getExtensions(ErrorOptionsProvider.EP_NAME)) {
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
    for (ErrorOptionsProvider optionsProvider : Extensions.getExtensions(ErrorOptionsProvider.EP_NAME)) {
      isModified |= optionsProvider.isModified();
    }
    if (isModified) return true;
    return false;
  }


  public void disposeUIResources() {
    for (ErrorOptionsProvider optionsProvider : Extensions.getExtensions(ErrorOptionsProvider.EP_NAME)) {
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
