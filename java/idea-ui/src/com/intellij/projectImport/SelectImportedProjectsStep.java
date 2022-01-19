// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.projectImport;

import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * @author Vladislav.Kaznacheev
 */
public abstract class SelectImportedProjectsStep<T> extends ProjectImportWizardStep {
  private final JPanel panel;
  protected final ElementsChooser<T> fileChooser;
  private final JCheckBox openModuleSettingsCheckBox;

  public SelectImportedProjectsStep(WizardContext context) {
    super(context);
    fileChooser = new ElementsChooser<>(true) {
      @Override
      protected String getItemText(@NotNull T item) {
        return getElementText(item);
      }

      @Override
      protected Icon getItemIcon(@NotNull final T item) {
        return getElementIcon(item);
      }
    };

    panel = new JPanel(new GridLayoutManager(3, 1, JBInsets.emptyInsets(), -1, -1));

    panel.add(fileChooser, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_BOTH,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null,
                                               null));

    AbstractAction selectAllAction = new AbstractAction(RefactoringBundle.message("select.all.button")) {
      @Override
      public void actionPerformed(ActionEvent e) {
        fileChooser.setAllElementsMarked(true);
      }
    };
    AbstractAction unselectAllAction = new AbstractAction(RefactoringBundle.message("unselect.all.button")) {
      @Override
      public void actionPerformed(ActionEvent e) {
        fileChooser.setAllElementsMarked(false);
      }
    };
    JComponent actionToolbar = Box.createHorizontalBox();
    actionToolbar.add(Box.createHorizontalGlue());
    actionToolbar.add(new JButton(selectAllAction));
    actionToolbar.add(new JButton(unselectAllAction));
    panel.add(actionToolbar, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_HORIZONTAL,
                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK, null, null, null));

    openModuleSettingsCheckBox = new JCheckBox(JavaUiBundle.message("project.import.show.settings.after"));
    panel.add(openModuleSettingsCheckBox, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_SOUTH, GridConstraints.FILL_HORIZONTAL,
                                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                              GridConstraints.SIZEPOLICY_FIXED, null, null, null));
  }

  @Nullable
  protected Icon getElementIcon(final T item) {
    return null;
  }

  protected abstract @NlsSafe String getElementText(final T item);

  @Override
  public JComponent getComponent() {
    return panel;
  }

  protected boolean isElementEnabled(T element) {
    return true;
  }

  @Override
  public void updateStep() {
    fileChooser.clear();
    for (T element : getContext().getList()) {
      boolean isEnabled = isElementEnabled(element);
      fileChooser.addElement(element, isEnabled && getContext().isMarked(element));
      if (!isEnabled) {
        fileChooser.disableElement(element);
      }
    }

    fileChooser.setBorder(IdeBorderFactory.createTitledBorder(
      JavaUiBundle.message("project.import.select.title", getContext().getName()), false));
    openModuleSettingsCheckBox.setSelected(getBuilder().isOpenProjectSettingsAfter());
  }

  @Override
  public boolean validate() throws ConfigurationException {
    getContext().setList(fileChooser.getMarkedElements());
    if (fileChooser.getMarkedElements().size() == 0) {
      throw new ConfigurationException(JavaUiBundle.message("select.imported.projects.dialog.message.nothing.found"),
                                       JavaUiBundle.message("select.imported.projects.dialog.title.unable.to.proceed"));
    }
    return true;
  }

  @Override
  public void updateDataModel() {}

  @Override
  public void onStepLeaving() {
    super.onStepLeaving();
    getContext().setOpenProjectSettingsAfter(openModuleSettingsCheckBox.isSelected());
  }

  public ProjectImportBuilder<T> getContext() {
    return getBuilder();
  }
}