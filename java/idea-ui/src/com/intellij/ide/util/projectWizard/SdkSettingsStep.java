/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.JdkComboBox;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import static java.awt.GridBagConstraints.*;

/**
 * @author Dmitry Avdeev
 */
public class SdkSettingsStep extends ModuleWizardStep {
  protected final JdkComboBox myJdkComboBox;
  protected final WizardContext myWizardContext;
  protected final ProjectSdksModel myModel;
  private final ModuleBuilder myModuleBuilder;
  private final JPanel myJdkPanel;

  public SdkSettingsStep(SettingsStep settingsStep, @NotNull ModuleBuilder moduleBuilder,
                         @NotNull Condition<SdkTypeId> sdkTypeIdFilter) {
    this(settingsStep, moduleBuilder, sdkTypeIdFilter, null);
  }

  public SdkSettingsStep(SettingsStep settingsStep,
                         @NotNull ModuleBuilder moduleBuilder,
                         @NotNull Condition<SdkTypeId> sdkTypeIdFilter,
                         @Nullable Condition<Sdk> sdkFilter) {
    this(settingsStep.getContext(), moduleBuilder, sdkTypeIdFilter, sdkFilter);
    if (!isEmpty()) {
      settingsStep.addSettingsField(getSdkFieldLabel(settingsStep.getContext().getProject()), myJdkPanel);
    }
  }

  public SdkSettingsStep(WizardContext context,
                         @NotNull ModuleBuilder moduleBuilder,
                         @NotNull Condition<SdkTypeId> sdkTypeIdFilter,
                         @Nullable Condition<Sdk> sdkFilter) {
    myModuleBuilder = moduleBuilder;

    myWizardContext = context;
    myModel = new ProjectSdksModel();
    Project project = myWizardContext.getProject();
    myModel.reset(project);

    if (sdkFilter == null) {
      sdkFilter = JdkComboBox.getSdkFilter(sdkTypeIdFilter);
    }

    myJdkComboBox = new JdkComboBox(myModel, sdkTypeIdFilter, sdkFilter, sdkTypeIdFilter, true);
    myJdkPanel = new JPanel(new GridBagLayout());

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

    Sdk sdk = getPreselectedSdk(project, component.getValue(selectedJdkProperty), sdkTypeIdFilter);
    myJdkComboBox.setSelectedJdk(sdk);

    JButton button = new JButton("Ne\u001Bw...");
    myJdkComboBox.setSetupButton(button, project, myModel,
                                 project == null ? new JdkComboBox.NoneJdkComboBoxItem() : new JdkComboBox.ProjectJdkComboBoxItem(),
                                 null,
                                 false);

    myJdkPanel.add(myJdkComboBox, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, CENTER, HORIZONTAL, JBUI.emptyInsets(), 0, 0));
    myJdkPanel.add(myJdkComboBox.getSetUpButton(), new GridBagConstraints(1, 0, 1, 1, 0, 0, WEST, NONE, JBUI.insetsLeft(4), 0, 0));
    if (myJdkComboBox.getItemCount() == 0) {
      SdkType type = ContainerUtil.find(SdkType.getAllTypes(), sdkTypeIdFilter);
      if (type != null && type.getDownloadSdkUrl() != null) {
        HyperlinkLabel label = new HyperlinkLabel("Download " + type.getPresentableName());
        label.setHyperlinkTarget(type.getDownloadSdkUrl());
        myJdkPanel.add(label, new GridBagConstraints(0, 1, 1, 1, 0, 0, WEST, NONE, JBUI.insetsTop(4), 0, 0));
      }
    }
  }

  private Sdk getPreselectedSdk(Project project, String lastUsedSdk, Condition<SdkTypeId> sdkFilter) {
    if (project != null) {
      Sdk sdk = ProjectRootManager.getInstance(project).getProjectSdk();
      if (sdk != null && myModuleBuilder.isSuitableSdkType(sdk.getSdkType())) {
        // use project SDK
        //noinspection unchecked
        myJdkComboBox.insertItemAt(new JdkComboBox.ProjectJdkComboBoxItem(), 0);
        return null;
      }
    }
    if (lastUsedSdk != null) {
      Sdk sdk = ProjectJdkTable.getInstance().findJdk(lastUsedSdk);
      if (sdk != null && myModuleBuilder.isSuitableSdkType(sdk.getSdkType())) {
        return sdk;
      }
    }
    // set default project SDK
    Project defaultProject = ProjectManager.getInstance().getDefaultProject();
    Sdk selected = ProjectRootManager.getInstance(defaultProject).getProjectSdk();
    if (selected != null && sdkFilter.value(selected.getSdkType())) {
      return selected;
    }
    return myJdkComboBox.getSelectedJdk();
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
    if (myJdkComboBox.getSelectedJdk() == null &&
        !(item instanceof JdkComboBox.ProjectJdkComboBoxItem) &&
        !(item instanceof JdkComboBox.SuggestedJdkItem)) {
      if (Messages.showDialog(getNoSdkMessage(),
                                       IdeBundle.message("title.no.jdk.specified"),
                                       new String[]{CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText()}, 1, Messages.getWarningIcon()) != Messages.YES) {
        return false;
      }
    }
    try {
      if (item instanceof JdkComboBox.SuggestedJdkItem) {
        SdkType type = ((JdkComboBox.SuggestedJdkItem)item).getSdkType();
        String path = ((JdkComboBox.SuggestedJdkItem)item).getPath();
        myModel.addSdk(type, path, sdk -> {
          myJdkComboBox.reloadModel(new JdkComboBox.ActualJdkComboBoxItem(sdk), myWizardContext.getProject());
          myJdkComboBox.setSelectedJdk(sdk);
        });
      }
      myModel.apply(null, true);
    } catch (ConfigurationException e) {
      //IDEA-98382 We should allow Next step if user has wrong SDK
      if (Messages.showDialog(e.getMessage() + "/nDo you want to proceed?",
                                       e.getTitle(),
                                       new String[]{CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText()}, 1, Messages.getWarningIcon()) != Messages.YES) {
        return false;
      }
    }
    return true;
  }

  protected String getNoSdkMessage() {
    return IdeBundle.message("prompt.confirm.project.no.jdk");
  }
}
