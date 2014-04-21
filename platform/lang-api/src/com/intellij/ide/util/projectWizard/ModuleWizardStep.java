/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.ide.wizard.StepAdapter;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.FieldPanel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;

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

  @NonNls public String getHelpId() {
    return null;
  }

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

  public static FieldPanel createFieldPanel(final JTextField field, final String labelText, final BrowseFilesListener browseButtonActionListener) {
    final FieldPanel fieldPanel = new FieldPanel(field, labelText, null, browseButtonActionListener, null);
    fieldPanel.getFieldLabel().setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD));
    return fieldPanel;
  }

  @Override
  public String toString() {
    return getName();
  }
}
