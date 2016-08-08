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
package com.intellij.ide.projectWizard;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.platform.ProjectTemplate;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

/**
 * @author Dmitry Avdeev
 */
public class ChooseTemplateStep extends ModuleWizardStep {

  private final WizardContext myWizardContext;
  private final ProjectTypeStep myProjectTypeStep;

  private JPanel myPanel;
  private ProjectTemplateList myTemplateList;
  private JBCheckBox myCreateFromTemplateCheckBox;

  public ChooseTemplateStep(WizardContext wizardContext, ProjectTypeStep projectTypeStep) {
    myWizardContext = wizardContext;
    myProjectTypeStep = projectTypeStep;
    myCreateFromTemplateCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myTemplateList.setEnabled(myCreateFromTemplateCheckBox.isSelected());
        if (myCreateFromTemplateCheckBox.isSelected()) {
          myTemplateList.getList().requestFocus();
        }
      }
    });
    myTemplateList.setEnabled(false);
  }

  @Override
  public boolean isStepVisible() {
    return myWizardContext.isCreatingNewProject() && !myProjectTypeStep.getAvailableTemplates().isEmpty();
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public void updateStep() {
    myTemplateList.setTemplates(new ArrayList<>(myProjectTypeStep.getAvailableTemplates()), false);
  }

  @Override
  public void updateDataModel() {
    if (myCreateFromTemplateCheckBox.isSelected()) {
      myWizardContext.setProjectTemplate(myTemplateList.getSelectedTemplate());
    }
  }

  public ProjectTemplateList getTemplateList() {
    return myTemplateList;
  }

  @TestOnly
  public boolean setSelectedTemplate(String name) {
    myCreateFromTemplateCheckBox.setSelected(true);
    return myTemplateList.setSelectedTemplate(name);
  }

}
