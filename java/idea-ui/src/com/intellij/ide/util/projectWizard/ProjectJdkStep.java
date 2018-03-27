/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.importProject.ProjectDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.ProjectJdksConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.util.ui.JBUI;

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

  public JComponent getPreferredFocusedComponent() {
    return myJDKsComponent;
  }

  public String getHelpId() {
    return "reference.dialogs.new.project.fromScratch.sdk";
  }

  public JComponent getComponent() {
    final JLabel label = new JLabel(IdeBundle.message("prompt.please.select.project.jdk"));
    label.setUI(new MultiLineLabelUI());
    final JPanel panel = new JPanel(new GridBagLayout()){
      public Dimension getPreferredSize() {
        return new Dimension(-1, 200);
      }
    };
    panel.add(label, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0));
    myJDKsComponent.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
    panel.add(myJDKsComponent, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, JBUI.emptyInsets(), 0, 0));
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

  public void updateDataModel() {
    myContext.setProjectJdk(getJdk());
  }


  public Sdk getJdk() {
    return myProjectJdksConfigurable.getSelectedJdk();
  }

  public Icon getIcon() {
    return myContext.getStepIcon();
  }

  public boolean validate() throws ConfigurationException {
    final Sdk jdk = myProjectJdksConfigurable.getSelectedJdk();
    if (jdk == null && !ApplicationManager.getApplication().isUnitTestMode()) {
      int result = Messages.showOkCancelDialog(IdeBundle.message("prompt.confirm.project.no.jdk"),
                                               IdeBundle.message("title.no.jdk.specified"), Messages.getWarningIcon());
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

  public void disposeUIResources() {
    super.disposeUIResources();
    myProjectJdksConfigurable.disposeUIResources();
  }

  public void setProjectDescriptor(ProjectDescriptor projectDescriptor) {
    myProjectDescriptor = projectDescriptor;
  }
}
