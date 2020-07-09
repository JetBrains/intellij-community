// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard;

import com.intellij.CommonBundle;
import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.util.newProjectWizard.AddModuleWizard;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.ui.configuration.JdkComboBox;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Eugene Zhuravlev
 */
public class ProjectJdkForModuleStep extends ModuleWizardStep {
  @NotNull private final Project myProject;
  @NotNull private final ProjectSdksModel mySdksModel;
  @NotNull private final JdkComboBox myJdkChooser;
  @NotNull private final JPanel myPanel;
  @NotNull private final WizardContext myContext;
  @Nullable private final String myHelpId;
  @NotNull private final JButton mySetAsDefaultButton;

  public ProjectJdkForModuleStep(@NotNull final WizardContext context, @Nullable final SdkType type) {
    this(context, type, null);
  }

  public ProjectJdkForModuleStep(@NotNull final WizardContext context, @Nullable final SdkType type, @Nullable @NonNls String helpId) {
    myContext = context;
    myHelpId = helpId;
    Project project = context.getProject();
    myProject = project != null ? project : ProjectManager.getInstance().getDefaultProject();

    final ProjectStructureConfigurable projectConfig = ProjectStructureConfigurable.getInstance(myProject);
    mySdksModel = projectConfig.getProjectJdksModel();
    myJdkChooser = new JdkComboBox(myProject,
                                   mySdksModel,
                                   sdk -> sdk instanceof SdkType && (type == null || type.equals(sdk)),
                                   null,
                                   null,
                                   null);

    myPanel = new JPanel(new GridBagLayout());
    myPanel.setBorder(BorderFactory.createEtchedBorder());

    final JLabel label = new JLabel(JavaUiBundle.message("prompt.please.select.module.jdk", type == null ? "SDK" : type.getPresentableName()));
    label.setUI(new MultiLineLabelUI());
    myPanel.add(label, new GridBagConstraints(0, 0, 2, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST,
                                              GridBagConstraints.HORIZONTAL, JBInsets.create(8, 10), 0, 0));

    final JLabel jdkLabel = new JLabel(JavaUiBundle.message("label.project.jdk"));
    jdkLabel.setFont(StartupUiUtil.getLabelFont().deriveFont(Font.BOLD));
    myPanel.add(jdkLabel, new GridBagConstraints(0, 1, 1, 1, 0, 0.0, GridBagConstraints.NORTHWEST,
                                                 GridBagConstraints.NONE, JBUI.insets(8, 10, 0, 10), 0, 0));
    jdkLabel.setLabelFor(myJdkChooser);

    myPanel.add(myJdkChooser, new GridBagConstraints(1, 1, 1, 1, 1.0, 0, GridBagConstraints.NORTHWEST,
                                                     GridBagConstraints.HORIZONTAL, JBUI.insets(2, 10, 10, 5), 0, 0));

    mySetAsDefaultButton = new JButton(JavaUiBundle.message("button.set.default"));
    mySetAsDefaultButton.setMnemonic('D');
    myPanel.add(mySetAsDefaultButton, new GridBagConstraints(1, 2, 1, 1, 0.0, 1.0, GridBagConstraints.NORTHWEST,
                                                             GridBagConstraints.NONE, JBUI.insets(2, 10, 10, 5), 0, 0));

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

    myJdkChooser.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        mySetAsDefaultButton.setEnabled(getJdk() != ProjectRootManagerEx.getInstanceEx(defaultProject).getProjectSdk());
      }
    });
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myJdkChooser;
  }

  @Nullable
  @Override
  public String getHelpId() {
    return myHelpId;
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
    mySdksModel.reset(myProject);
    myJdkChooser.reloadModel();
    Sdk defaultJdk = getDefaultJdk();
    if (defaultJdk != null) {
      myJdkChooser.setSelectedJdk(defaultJdk);
    }
    mySetAsDefaultButton.setEnabled(defaultJdk != null);
  }

  @Nullable
  public Sdk getJdk() {
    return myJdkChooser.getSelectedJdk();
  }

  /**
   * @deprecated this method does return an empty array
   */
  @Deprecated
  public Object @NotNull [] getAllJdks() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return myContext.getStepIcon();
  }

  @Nullable
  private Sdk getDefaultJdk() {
    Project defaultProject = ProjectManagerEx.getInstanceEx().getDefaultProject();
    Sdk sdk = ProjectRootManagerEx.getInstanceEx(defaultProject).getProjectSdk();
    if (sdk == null) {
      sdk = AddModuleWizard.getMostRecentSuitableSdk(myContext);
    }
    return sdk;
  }

  @Override
  public boolean validate() {
    final Sdk jdk = myJdkChooser.getSelectedJdk();
    if (jdk == null) {
      int result = Messages
        .showOkCancelDialog(JavaUiBundle.message("prompt.confirm.project.no.jdk"),
                            JavaUiBundle.message("title.no.jdk.specified"),
                            Messages.getOkButton(),
                            Messages.getCancelButton(),
                            Messages.getWarningIcon(), null);
      if (result != Messages.OK) {
        return false;
      }
    }

    try {
      mySdksModel.apply(null, true);
    } catch (ConfigurationException e) {
      //IDEA-98382 We should allow Next step if user has wrong SDK
      if (Messages.showDialog(JavaUiBundle.message("dialog.message.0.do.you.want.to.proceed", e.getMessage()),
                              e.getTitle(),
                              new String[]{CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText()}, 1, Messages.getWarningIcon()) != Messages.YES) {
        return false;
      }
    }

    return true;
  }
}