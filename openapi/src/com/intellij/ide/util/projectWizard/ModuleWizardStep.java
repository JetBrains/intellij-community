/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
import com.intellij.ide.wizard.StepAdapter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.FieldPanel;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

import org.jetbrains.annotations.NonNls;

public abstract class ModuleWizardStep extends StepAdapter{
  
  public final static GridBagConstraints LABEL_CONSTRAINT = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0,
                                                                    GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                                                    new Insets(8, 10, 0, 10), 0, 0);

  protected static final Icon ICON = IconLoader.getIcon("/addmodulewizard.png");
  public static final ModuleWizardStep[] EMPTY_ARRAY = new ModuleWizardStep[0];

  public abstract JComponent getComponent();
  public abstract void updateDataModel();

  @NonNls public String getHelpId() {
    return null;
  }

  public boolean validate() {
    return true;
  }

  public void onStepLeaving() {
    // empty by default
  }

  public void updateStep() {
    // empty by default
  }

  public Icon getIcon() {
    return null;
  }

  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  public boolean isStepVisible() {
    return true;
  }

  protected static FieldPanel createFieldPanel(final JTextField field, final String labelText, final BrowseFilesListener browseButtonActionListener) {
    final FieldPanel fieldPanel = new FieldPanel(field, labelText, null, browseButtonActionListener, null);
    fieldPanel.getFieldLabel().setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD));
    return fieldPanel;
  }
}
