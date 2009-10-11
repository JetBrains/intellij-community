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
package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.ide.IdeBundle;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemListener;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 21, 2004
 */
public class ModuleCreationPromptStep extends ModuleWizardStep {
  private static final Icon NEW_PROJECT_ICON = IconLoader.getIcon("/newprojectwizard.png");
  private final JPanel myPanel;
  private final JRadioButton myRbCreateSingle;
  private final JRadioButton myRbCreateMultiple;

  public ModuleCreationPromptStep() {
    myPanel = new JPanel(new GridBagLayout());
    myPanel.setBorder(BorderFactory.createEtchedBorder());
    final String promptText =
      IdeBundle.message("prompt.single.or.multi.module.project", ApplicationNamesInfo.getInstance().getProductName());
    final JLabel promptLabel = new JLabel(promptText);
    promptLabel.setUI(new MultiLineLabelUI());
    myPanel.add(promptLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(8, 10, 8, 10), 0, 0));

    myRbCreateSingle = new JRadioButton(IdeBundle.message("radio.create.&single.module.project"), true);
    myRbCreateMultiple = new JRadioButton(IdeBundle.message("radio.create.configure.&multi.module.project"));
    myPanel.add(myRbCreateSingle, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(8, 6, 0, 6), 0, 0));
    myPanel.add(myRbCreateMultiple, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(5, 6, 0, 6), 0, 0));

    ButtonGroup group = new ButtonGroup();
    group.add(myRbCreateSingle);
    group.add(myRbCreateMultiple);

  }

  public JComponent getPreferredFocusedComponent() {
    return myRbCreateSingle;
  }

  public String getHelpId() {
    return "project.new.page3";
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public void updateDataModel() {
  }

  public Icon getIcon() {
    return NEW_PROJECT_ICON;
  }

  public boolean isCreateModule() {
    return myRbCreateSingle.isSelected();
  }

  public void addCreateModuleChoiceListener(ItemListener listener) {
    myRbCreateSingle.addItemListener(listener);
  }
}
