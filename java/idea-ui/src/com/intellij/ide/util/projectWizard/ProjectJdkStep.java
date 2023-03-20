// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.util.projectWizard;

import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.util.importProject.ProjectDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.ProjectJdksConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.util.ui.JBInsets;

import javax.swing.*;
import java.awt.*;

/**
 * @author Eugene Zhuravlev
 */
public class ProjectJdkStep extends ModuleWizardStep {
  private final WizardContext myContext;
  private ProjectDescriptor myProjectDescriptor;

  protected final ProjectJdksConfigurable myProjectJdksConfigurable;

  private final JComponent myJDKsComponent;

  public ProjectJdkStep(final WizardContext context) {
    myContext = context;
    myProjectJdksConfigurable = new ProjectJdksConfigurable(ProjectManager.getInstance().getDefaultProject());
    myProjectJdksConfigurable.reset();
    myJDKsComponent = myProjectJdksConfigurable.createComponent();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myJDKsComponent;
  }

  @Override
  public String getHelpId() {
    return "reference.dialogs.new.project.fromScratch.sdk";
  }

  @Override
  public JComponent getComponent() {
    final JLabel label = new JLabel(JavaUiBundle.message("prompt.please.select.project.jdk"));
    label.setUI(new MultiLineLabelUI());
    final JPanel panel = new JPanel(new GridBagLayout()){
      @Override
      public Dimension getPreferredSize() {
        return new Dimension(-1, 200);
      }
    };
    panel.add(label, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                                            JBInsets.emptyInsets(), 0, 0));
    myJDKsComponent.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
    panel.add(myJDKsComponent, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                                                      JBInsets.emptyInsets(), 0, 0));
    return panel;
  }

  @Override
  public void updateStep() {
    final Sdk jdk = myContext.getProjectJdk();
    if (jdk == null) {
      JavaSdkVersion requiredJdkVersion = myProjectDescriptor != null ? myProjectDescriptor.getRequiredJdkVersion() : null;
      if (requiredJdkVersion != null) {
        myProjectJdksConfigurable.selectJdkVersion(requiredJdkVersion);
      }
    }
  }

  @Override
  public void updateDataModel() {
    myContext.setProjectJdk(getJdk());
  }


  public Sdk getJdk() {
    return myProjectJdksConfigurable.getSelectedJdk();
  }

  @Override
  public Icon getIcon() {
    return myContext.getStepIcon();
  }

  @Override
  public boolean validate() throws ConfigurationException {
    final Sdk jdk = myProjectJdksConfigurable.getSelectedJdk();
    if (jdk == null && !ApplicationManager.getApplication().isUnitTestMode()) {
      int result = Messages.showOkCancelDialog(JavaUiBundle.message("prompt.confirm.project.no.jdk"),
                                               JavaUiBundle.message("title.no.jdk.specified"), Messages.getWarningIcon());
      if (result != Messages.OK) {
        return false;
      }
    }
    myProjectJdksConfigurable.apply();
    return true;
  }

  @Override
  public String getName() {
    return "Project JDK";
  }

  @Override
  public void disposeUIResources() {
    super.disposeUIResources();
    myProjectJdksConfigurable.disposeUIResources();
  }

  public void setProjectDescriptor(ProjectDescriptor projectDescriptor) {
    myProjectDescriptor = projectDescriptor;
  }
}
