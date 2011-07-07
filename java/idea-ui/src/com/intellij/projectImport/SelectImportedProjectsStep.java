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
package com.intellij.projectImport;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Vladislav.Kaznacheev
 */
public abstract class SelectImportedProjectsStep<T> extends ProjectImportWizardStep {
  private final JPanel panel;
  protected final ElementsChooser<T> fileChooser;
  private final JCheckBox openModuleSettingsCheckBox;

  public SelectImportedProjectsStep(WizardContext context) {
    super(context);
    fileChooser = new ElementsChooser<T>(true) {
      protected String getItemText(@NotNull T item) {
        return getElementText(item);
      }

      protected Icon getItemIcon(final T item) {
        return getElementIcon (item);
      }
    };

    panel = new JPanel(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));

    panel.add(fileChooser, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_BOTH,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null,
                                               null));
    openModuleSettingsCheckBox = new JCheckBox(IdeBundle.message("project.import.show.settings.after"));
    panel.add(openModuleSettingsCheckBox, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_SOUTH, GridConstraints.FILL_HORIZONTAL,
                                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                              GridConstraints.SIZEPOLICY_FIXED, null, null, null));
  }

  @Nullable
  protected Icon getElementIcon(final T item) {
    return null;    
  }

  protected abstract String getElementText(final T item);

  public JComponent getComponent() {
    return panel;
  }

  public void updateStep() {
    fileChooser.clear();
    for (T element : getContext().getList()) {
      fileChooser.addElement(element, getContext().isMarked(element));
    }
    fileChooser.setBorder(IdeBorderFactory.createTitledBorder(IdeBundle.message("project.import.select.title", getContext().getName())));
    openModuleSettingsCheckBox.setSelected(getBuilder().isOpenProjectSettingsAfter());
  }

  public boolean validate() throws ConfigurationException {
    getContext().setList(fileChooser.getMarkedElements());
    if (fileChooser.getMarkedElements().size() == 0) {
      throw new ConfigurationException("Nothing found to import", "Unable to proceed");
    }
    return true;
  }

  public void updateDataModel() {}

  public void onStepLeaving() {
    super.onStepLeaving();
    getContext().setOpenProjectSettingsAfter(openModuleSettingsCheckBox.isSelected());
  }

  public ProjectImportBuilder<T> getContext() {
    return getBuilder();
  }
}

