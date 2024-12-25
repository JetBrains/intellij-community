// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.projectWizard;

import com.intellij.ide.wizard.CommitStepException;
import com.intellij.ide.wizard.StepAdapter;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

public abstract class ModuleWizardStep extends StepAdapter {

  public static final ModuleWizardStep[] EMPTY_ARRAY = {};

  @Override
  public abstract JComponent getComponent();

  /** Commits data from UI into ModuleBuilder and WizardContext */
  public abstract void updateDataModel();

  /** Update UI from ModuleBuilder and WizardContext */
  public void updateStep() {
    // empty by default
  }

  public @NonNls String getHelpId() {
    return null;
  }

  /**
   * Validates user input before {@link #updateDataModel()} is called.
   *
   * @return {@code true} if input is valid, {@code false} otherwise
   * @throws ConfigurationException if input is not valid and needs user attention. Exception message will be displayed to user
   */
  public boolean validate() throws ConfigurationException {
    return true;
  }

  public void onStepLeaving() {
  }

  public void onWizardFinished() throws CommitStepException {
  }

  public boolean isStepVisible() {
    return true;
  }

  public String getName() {
    return getClass().getName();
  }

  public void disposeUIResources() {
  }

  @Override
  public String toString() {
    return getName();
  }
}
