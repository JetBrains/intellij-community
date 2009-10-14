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

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModel;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectJdksModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ModuleProjectStructureElement;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * User: anna
 * Date: 05-Jun-2006
 */
public class ModuleJdkConfigurable implements Disposable {
  private JdkComboBox myCbModuleJdk;
  private Sdk mySelectedModuleJdk = null;
  private final ModifiableRootModel myRootModel;
  private JPanel myJdkPanel;
  private ClasspathEditor myModuleEditor;
  private final ProjectJdksModel myJdksModel;
  private boolean myFreeze = false;
  private final SdkModel.Listener myListener = new SdkModel.Listener() {
    public void sdkAdded(Sdk sdk) {
      reloadModel();
    }

    public void beforeSdkRemove(Sdk sdk) {
      reloadModel();
    }

    public void sdkChanged(Sdk sdk, String previousName) {
      reloadModel();
    }

    public void sdkHomeSelected(Sdk sdk, String newSdkHome) {
      reloadModel();
    }
  };

  public ModuleJdkConfigurable(ClasspathEditor moduleEditor, ModifiableRootModel model, ProjectJdksModel jdksModel) {
    myModuleEditor = moduleEditor;
    myRootModel = model;
    myJdksModel = jdksModel;
    myJdksModel.addListener(myListener);
    init();
  }

  /**
   * @return null if JDK should be inherited
   */
  @Nullable
  public Sdk getSelectedModuleJdk() {
    return myJdksModel.findSdk(mySelectedModuleJdk);
  }

  public boolean isInheritJdk() {
    return myCbModuleJdk.getSelectedItem()instanceof JdkComboBox.ProjectJdkComboBoxItem;
  }

  public JComponent createComponent() {
    return myJdkPanel;
  }

  private void reloadModel() {
    myFreeze = true;
    myCbModuleJdk.reloadModel(new JdkComboBox.ProjectJdkComboBoxItem(), myRootModel.getModule().getProject());
    reset();
    myFreeze = false;
  }

  private void init() {
    myJdkPanel = new JPanel(new GridBagLayout());
    myCbModuleJdk = new JdkComboBox(myJdksModel);
    myCbModuleJdk.insertItemAt(new JdkComboBox.ProjectJdkComboBoxItem(), 0);
    myCbModuleJdk.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (myFreeze) return;
        final Sdk oldJdk = myRootModel.getSdk();
        mySelectedModuleJdk = myCbModuleJdk.getSelectedJdk();
        final Sdk selectedModuleJdk = getSelectedModuleJdk();
        if (selectedModuleJdk != null) {
          myRootModel.setSdk(selectedModuleJdk);
        }
        else {
          myRootModel.inheritSdk();
        }
        clearCaches(oldJdk, selectedModuleJdk);
        myModuleEditor.flushChangesToModel();
      }
    });
    myJdkPanel.add(new JLabel(ProjectBundle.message("module.libraries.target.jdk.module.radio")),
                   new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(12, 6, 12, 0), 0, 0));
    myJdkPanel.add(myCbModuleJdk, new GridBagConstraints(1, 0, 1, 1, 0, 1.0,
                                                         GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                                         new Insets(6, 6, 12, 0), 0, 0));
    final Project project = myRootModel.getModule().getProject();
    final JButton setUpButton = myCbModuleJdk
      .createSetupButton(project, myJdksModel, new JdkComboBox.ProjectJdkComboBoxItem(), new Condition<Sdk>(){
        public boolean value(Sdk jdk) {
          final Sdk projectJdk = myJdksModel.getProjectJdk();
          if (projectJdk == null){
            final int res =
              Messages.showYesNoDialog(myJdkPanel,
                                       ProjectBundle.message("project.roots.no.jdk.on.project.message"),
                                       ProjectBundle.message("project.roots.no.jdk.on.projecct.title"),
                                       Messages.getInformationIcon());
            if (res == DialogWrapper.OK_EXIT_CODE){
              myJdksModel.setProjectJdk(jdk);
              return true;
            }
          }
          return false;
        }
      }, true);
    myJdkPanel.add(setUpButton, new GridBagConstraints(2, 0, 1, 1, 0, 0,
                                                       GridBagConstraints.WEST, GridBagConstraints.NONE,
                                                       new Insets(0, 4, 7, 0), 0, 0));
    myCbModuleJdk.appendEditButton(myRootModel.getModule().getProject(), myJdkPanel, new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 1.0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 4, 7, 0), 0, 0) , new Computable<Sdk>() {
      @Nullable
      public Sdk compute() {
        return myRootModel.getSdk();
      }
    });
  }

  private void clearCaches(final Sdk oldJdk, final Sdk selectedModuleJdk) {
    final Module module = myRootModel.getModule();
    final Project project = module.getProject();
    final StructureConfigurableContext context = ModuleStructureConfigurable.getInstance(project).getContext();
    context.getDaemonAnalyzer().queueUpdate(new ModuleProjectStructureElement(context, module));
  }

  public void reset() {
    myFreeze = true;
    final String jdkName = myRootModel.getSdkName();
    if (jdkName != null && !myRootModel.isSdkInherited()) {
      mySelectedModuleJdk = myJdksModel.findSdk(jdkName);
      if (mySelectedModuleJdk != null) {
        myCbModuleJdk.setSelectedJdk(mySelectedModuleJdk);
      } else {
        myCbModuleJdk.setInvalidJdk(jdkName);
        clearCaches(null, null);
      }
    }
    else {
      myCbModuleJdk.setSelectedJdk(null);
    }
    myFreeze = false;
  }

  public void dispose() {
    myModuleEditor = null;
    myCbModuleJdk = null;
    myJdkPanel = null;
    myJdksModel.removeListener(myListener);
  }
}
