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

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.JdkListConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 21, 2004
 */
public class ProjectJdkForModuleStep extends ModuleWizardStep {
  private static final Icon NEW_PROJECT_ICON = IconLoader.getIcon("/newprojectwizard.png");
  private final JdkChooserPanel myJdkChooser;
  private final JPanel myPanel;
  private final WizardContext myContext;
  private final SdkType myType;
  private boolean myInitialized = false;

  public ProjectJdkForModuleStep(final WizardContext context, final SdkType type) {
    myContext = context;
    myType = type;
    myJdkChooser = new JdkChooserPanel(getProject(context, type));

    myPanel = new JPanel(new GridBagLayout());
    myPanel.setBorder(BorderFactory.createEtchedBorder());

    final JLabel label = new JLabel(IdeBundle.message("prompt.please.select.module.jdk", type.getPresentableName()));
    label.setUI(new MultiLineLabelUI());
    myPanel.add(label, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST,
                                              GridBagConstraints.HORIZONTAL, new Insets(8, 10, 8, 10), 0, 0));

    final JLabel jdklabel = new JLabel(IdeBundle.message("label.project.jdk"));
    jdklabel.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD));
    myPanel.add(jdklabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST,
                                                 GridBagConstraints.NONE, new Insets(8, 10, 0, 10), 0, 0));

    myPanel.add(myJdkChooser, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST,
                                                     GridBagConstraints.BOTH, new Insets(2, 10, 10, 5), 0, 0));
    JButton configureButton = new JButton(IdeBundle.message("button.configure"));
    myPanel.add(configureButton, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 1.0, GridBagConstraints.NORTHWEST,
                                                        GridBagConstraints.NONE, new Insets(2, 0, 10, 5), 0, 0));

    configureButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {

        final Project project = getProject(context, type);
        final ProjectStructureConfigurable projectConfig = ProjectStructureConfigurable.getInstance(project);
        final JdkListConfigurable jdkConfig = JdkListConfigurable.getInstance(project);
        final ProjectSdksModel projectJdksModel = projectConfig.getProjectJdksModel();
        final boolean[] successfullyAdded = new boolean[1];
        projectJdksModel.doAdd(type, myPanel, new Consumer<Sdk>() {
          public void consume(final Sdk jdk) {
            successfullyAdded[0] = jdkConfig.addJdkNode(jdk, false);
            myJdkChooser.updateList(jdk, type);
          }
        });
        if (!successfullyAdded[0]) {
          try {
            projectJdksModel.apply(jdkConfig);
          }
          catch (ConfigurationException e1) {
            //name can't be wrong
          }
        }

      }
    });
  }

  @Nullable
  private static Project getProject(final WizardContext context, final SdkType type) {
    Project project = context.getProject();
    if (type != null && project == null) { //'module' step inside project creation
      project = ProjectManager.getInstance().getDefaultProject();
    }
    return project;
  }

  public JComponent getPreferredFocusedComponent() {
    return myJdkChooser.getPreferredFocusedComponent();
  }

  public String getHelpId() {
    return "project.new.page2";
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public void updateDataModel() {
    myContext.setProjectJdk(getJdk());
  }


  public void updateStep() {
    if (!myInitialized) { //lazy default project initialization
      myJdkChooser.fillList(myType);
      Sdk defaultJdk = getDefaultJdk();
      if (defaultJdk != null) {
        myJdkChooser.selectJdk(defaultJdk);
      }
      myInitialized = true;
    }
  }

  public Sdk getJdk() {
    return myJdkChooser.getChosenJdk();
  }

  public Icon getIcon() {
    return NEW_PROJECT_ICON;
  }

  @Nullable
  private static Sdk getDefaultJdk() {
    Project defaultProject = ProjectManagerEx.getInstanceEx().getDefaultProject();
    return ProjectRootManagerEx.getInstanceEx(defaultProject).getProjectJdk();
  }


  public boolean validate() {
    final Sdk jdk = myJdkChooser.getChosenJdk();
    if (jdk == null) {
      int result = Messages.showOkCancelDialog(IdeBundle.message("prompt.confirm.project.no.jdk"),
                                               IdeBundle.message("title.no.jdk.specified"), Messages.getWarningIcon());
      if (result != 0) {
        return false;
      }
    }
    return true;
  }


}
