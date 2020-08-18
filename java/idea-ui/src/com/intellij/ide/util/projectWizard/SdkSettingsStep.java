// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard;

import com.intellij.CommonBundle;
import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.JdkComboBox;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import static java.awt.GridBagConstraints.CENTER;
import static java.awt.GridBagConstraints.HORIZONTAL;

/**
 * @author Dmitry Avdeev
 */
public class SdkSettingsStep extends ModuleWizardStep {
  protected final JdkComboBox myJdkComboBox;
  protected final WizardContext myWizardContext;
  protected final ProjectSdksModel myModel;
  private final ModuleBuilder myModuleBuilder;
  private final JPanel myJdkPanel;

  public SdkSettingsStep(@NotNull SettingsStep settingsStep, @NotNull ModuleBuilder moduleBuilder,
                         @NotNull Condition<? super SdkTypeId> sdkTypeIdFilter) {
    this(settingsStep, moduleBuilder, sdkTypeIdFilter, null);
  }

  public SdkSettingsStep(@NotNull SettingsStep settingsStep,
                         @NotNull ModuleBuilder moduleBuilder,
                         @NotNull Condition<? super SdkTypeId> sdkTypeIdFilter,
                         @Nullable Condition<? super Sdk> sdkFilter) {
    this(settingsStep.getContext(), moduleBuilder, sdkTypeIdFilter, sdkFilter);
    if (!isEmpty()) {
      settingsStep.addSettingsField(getSdkFieldLabel(settingsStep.getContext().getProject()), myJdkPanel);
    }
  }

  public SdkSettingsStep(@NotNull WizardContext context,
                         @NotNull ModuleBuilder moduleBuilder,
                         @NotNull Condition<? super SdkTypeId> sdkTypeIdFilter,
                         @Nullable Condition<? super Sdk> sdkFilter) {
    myModuleBuilder = moduleBuilder;

    myWizardContext = context;
    myModel = new ProjectSdksModel();
    Project project = myWizardContext.getProject();
    myModel.reset(project);

    if (sdkFilter == null) {
      sdkFilter = JdkComboBox.getSdkFilter(sdkTypeIdFilter);
    }

    myJdkComboBox = new JdkComboBox(myWizardContext.getProject(), myModel, sdkTypeIdFilter, sdkFilter, sdkTypeIdFilter, null);
    myJdkPanel = new JPanel(new GridBagLayout());
    myJdkPanel.setFocusable(false);
    myJdkComboBox.getAccessibleContext().setAccessibleName(myJdkPanel.getAccessibleContext().getAccessibleName());

    final PropertiesComponent component = project == null ? PropertiesComponent.getInstance() : PropertiesComponent.getInstance(project);
    ModuleType moduleType = moduleBuilder.getModuleType();
    final String selectedJdkProperty = "jdk.selected." + (moduleType == null ? "" : moduleType.getId());
    myJdkComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        Sdk jdk = myJdkComboBox.getSelectedJdk();
        if (jdk != null) {
          component.setValue(selectedJdkProperty, jdk.getName());
        }
        onSdkSelected(jdk);
      }
    });

    preselectSdk(project, component.getValue(selectedJdkProperty), sdkTypeIdFilter);
    myJdkPanel.add(myJdkComboBox, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, CENTER, HORIZONTAL, JBUI.emptyInsets(), 0, 0));
  }

  private void preselectSdk(Project project, String lastUsedSdk, Condition<? super SdkTypeId> sdkFilter) {
    myJdkComboBox.reloadModel();

    if (project != null) {
      Sdk sdk = ProjectRootManager.getInstance(project).getProjectSdk();
      if (sdk != null && myModuleBuilder.isSuitableSdkType(sdk.getSdkType())) {
        // use project SDK
        myJdkComboBox.setSelectedItem(myJdkComboBox.showProjectSdkItem());
        return;
      }
    }

    if (lastUsedSdk != null) {
      Sdk sdk = ProjectJdkTable.getInstance().findJdk(lastUsedSdk);
      if (sdk != null && myModuleBuilder.isSuitableSdkType(sdk.getSdkType())) {
        myJdkComboBox.setSelectedJdk(sdk);
        return;
      }
    }

    // set default project SDK
    Project defaultProject = ProjectManager.getInstance().getDefaultProject();
    Sdk selected = ProjectRootManager.getInstance(defaultProject).getProjectSdk();
    if (selected != null && sdkFilter.value(selected.getSdkType())) {
      myJdkComboBox.setSelectedJdk(selected);
      return;
    }

    Sdk best = null;
    ComboBoxModel<JdkComboBox.JdkComboBoxItem> model = myJdkComboBox.getModel();
    for(int i = 0; i < model.getSize(); i++) {
      JdkComboBox.JdkComboBoxItem item = model.getElementAt(i);
      if (!(item instanceof JdkComboBox.ActualJdkComboBoxItem)) continue;

      Sdk jdk = item.getJdk();
      if (jdk == null) continue;

      SdkTypeId jdkType = jdk.getSdkType();
      if (!sdkFilter.value(jdkType)) continue;

      if (best == null) {
        best = jdk;
        continue;
      }

      SdkTypeId bestType = best.getSdkType();
      //it is in theory possible to have several SDK types here, let's just pick the first lucky type for now
      if (bestType == jdkType && bestType.versionComparator().compare(best, jdk) < 0) {
        best = jdk;
      }
    }

    if (best != null) {
      myJdkComboBox.setSelectedJdk(best);
    } else {
      myJdkComboBox.setSelectedItem(myJdkComboBox.showNoneSdkItem());
    }
  }

  protected void onSdkSelected(Sdk sdk) {}

  public boolean isEmpty() {
    return myJdkPanel.getComponentCount() == 0;
  }

  @NotNull
  protected String getSdkFieldLabel(@Nullable Project project) {
    return (project == null ? "Project" : "Module") + " \u001BSDK:";
  }

  @Override
  public JComponent getComponent() {
    return myJdkPanel;
  }

  @Override
  public void updateDataModel() {
    Project project = myWizardContext.getProject();
    Sdk jdk = myJdkComboBox.getSelectedJdk();
    if (project == null) {
      myWizardContext.setProjectJdk(jdk);
    }
    else {
      myModuleBuilder.setModuleJdk(jdk);
    }
  }

  @Override
  public boolean validate() throws ConfigurationException {
    JdkComboBox.JdkComboBoxItem item = myJdkComboBox.getSelectedItem();
    if (myJdkComboBox.getSelectedJdk() == null && !(item instanceof JdkComboBox.ProjectJdkComboBoxItem)) {
      if (Messages.showDialog(getNoSdkMessage(),
                                       JavaUiBundle.message("title.no.jdk.specified"),
                                       new String[]{CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText()}, 1, Messages.getWarningIcon()) != Messages.YES) {
        return false;
      }
    }
    try {
      myModel.apply(null, true);
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

  protected String getNoSdkMessage() {
    return JavaUiBundle.message("prompt.confirm.project.no.jdk");
  }
}
