package com.intellij.ide.util.projectWizard;

import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.ide.wizard.StepAdapter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.FieldPanel;

import javax.swing.*;
import java.awt.*;

public abstract class ModuleWizardStep extends StepAdapter{
  protected static final Icon ICON = IconLoader.getIcon("/addmodulewizard.png");
  public static final ModuleWizardStep[] EMPTY_ARRAY = new ModuleWizardStep[0];

  public abstract JComponent getComponent();
  public abstract void updateDataModel();

  public String getHelpId() {
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
    fieldPanel.getFieldLabel().setFont(UIManager.getFont("Label.font").deriveFont(Font.BOLD));
    return fieldPanel;
  }
}
