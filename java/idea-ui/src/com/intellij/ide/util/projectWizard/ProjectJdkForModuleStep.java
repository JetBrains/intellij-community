// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.newProjectWizard.AddModuleWizard;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Eugene Zhuravlev
 */
public class ProjectJdkForModuleStep extends ModuleWizardStep {
  private final JdkChooserPanel myJdkChooser;
  private final JPanel myPanel;
  private final WizardContext myContext;
  private final SdkType myType;
  private boolean myInitialized = false;
  private final JButton mySetAsDefaultButton;

  public ProjectJdkForModuleStep(final WizardContext context, final SdkType type) {
    myContext = context;
    myType = type;
    myJdkChooser = new JdkChooserPanel(getProject(context, type));

    myPanel = new JPanel(new GridBagLayout());
    myPanel.setBorder(BorderFactory.createEtchedBorder());

    final JLabel label = new JLabel(IdeBundle.message("prompt.please.select.module.jdk", type.getPresentableName()));
    label.setUI(new MultiLineLabelUI());
    myPanel.add(label, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST,
                                              GridBagConstraints.HORIZONTAL, JBUI.insets(8, 10), 0, 0));

    final JLabel jdkLabel = new JLabel(IdeBundle.message("label.project.jdk"));
    jdkLabel.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD));
    myPanel.add(jdkLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST,
                                                 GridBagConstraints.NONE, JBUI.insets(8, 10, 0, 10), 0, 0));

    myPanel.add(myJdkChooser, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 2, 1.0, 1.0, GridBagConstraints.NORTHWEST,
                                                     GridBagConstraints.BOTH, JBUI.insets(2, 10, 10, 5), 0, 0));
    JButton configureButton = new JButton(IdeBundle.message("button.configure"));
    myPanel.add(configureButton, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST,
                                                        GridBagConstraints.NONE, JBUI.insets(2, 0, 5, 5), 0, 0));
    mySetAsDefaultButton = new JButton("Set Default");
    mySetAsDefaultButton.setMnemonic('D');
    myPanel.add(mySetAsDefaultButton, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 1.0, GridBagConstraints.NORTHWEST,
                                                             GridBagConstraints.NONE, JBUI.insets(2, 0, 10, 5), 0, 0));

    configureButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {

        final Project project = getProject(context, type);
        final ProjectStructureConfigurable projectConfig = ProjectStructureConfigurable.getInstance(project);
        final JdkListConfigurable jdkConfig = JdkListConfigurable.getInstance(project);
        final ProjectSdksModel projectJdksModel = projectConfig.getProjectJdksModel();
        final boolean[] successfullyAdded = new boolean[1];
        projectJdksModel.reset(project);
        projectJdksModel.doAdd(myPanel, type, jdk -> {
          successfullyAdded[0] = jdkConfig.addJdkNode(jdk, false);
          myJdkChooser.updateList(jdk, type, projectJdksModel.getSdks());

          if (!successfullyAdded[0]) {
            try {
              projectJdksModel.apply(jdkConfig);
            }
            catch (ConfigurationException e1) {
              //name can't be wrong
            }
          }
        });
      }
    });

    final Project defaultProject = ProjectManagerEx.getInstanceEx().getDefaultProject();
    mySetAsDefaultButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {

        final Sdk jdk = getJdk();
        final Runnable runnable = () -> ProjectRootManagerEx.getInstanceEx(defaultProject).setProjectSdk(jdk);
        ApplicationManager.getApplication().runWriteAction(runnable);
        mySetAsDefaultButton.setEnabled(false);
      }
    });

    myJdkChooser.addSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        mySetAsDefaultButton.setEnabled(getJdk() != ProjectRootManagerEx.getInstanceEx(defaultProject).getProjectSdk());
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

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myJdkChooser.getPreferredFocusedComponent();
  }

  @Override
  public String getHelpId() {
    return "project.new.page2";
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public void updateDataModel() {
    myContext.setProjectJdk(getJdk());
  }

  @Override
  public void updateStep() {
    if (!myInitialized) { //lazy default project initialization
      myJdkChooser.fillList(myType, null);
      Sdk defaultJdk = getDefaultJdk(myContext);
      if (defaultJdk != null) {
        myJdkChooser.selectJdk(defaultJdk);
      }
      mySetAsDefaultButton.setEnabled(defaultJdk != null);
      myInitialized = true;
    }
  }

  public Sdk getJdk() {
    return myJdkChooser.getChosenJdk();
  }

  public Object[] getAllJdks() {
    return myJdkChooser.getAllJdks();
  }

  @Override
  public Icon getIcon() {
    return myContext.getStepIcon();
  }

  @Nullable
  private static Sdk getDefaultJdk(WizardContext context) {
    Project defaultProject = ProjectManagerEx.getInstanceEx().getDefaultProject();
    Sdk sdk = ProjectRootManagerEx.getInstanceEx(defaultProject).getProjectSdk();
    if (sdk == null) {
      sdk = AddModuleWizard.getMostRecentSuitableSdk(context);
    }
    return sdk;
  }

  @Override
  public boolean validate() {
    final Sdk jdk = myJdkChooser.getChosenJdk();
    if (jdk == null) {
      int result = Messages.showOkCancelDialog(IdeBundle.message("prompt.confirm.project.no.jdk"),
                                               IdeBundle.message("title.no.jdk.specified"), Messages.getWarningIcon());
      if (result != Messages.OK) {
        return false;
      }
    }
    return true;
  }
}