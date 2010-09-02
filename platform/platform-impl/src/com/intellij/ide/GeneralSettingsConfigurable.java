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
package com.intellij.ide;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.*;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.List;

/**
 * To provide an additional settings editor register implementation of {@link com.intellij.openapi.options.SearchableConfigurable} in the plugin.xml:
 * <p/>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;<br>
 * &nbsp;&nbsp;&lt;generalOptionsProvider instance="class-name"/&gt;<br>
 * &lt;/extensions&gt;
 * <p>
 * A new instance of the specified class will be created each time then the Settings dialog is opened
 */
public class GeneralSettingsConfigurable extends CompositeConfigurable<SearchableConfigurable> implements SearchableConfigurable {
  private static ExtensionPointName<GeneralSettingsConfigurableEP> EP_NAME = ExtensionPointName.create("com.intellij.generalOptionsProvider");
  
  private MyComponent myComponent;


  public void apply() throws ConfigurationException {
    super.apply();
    GeneralSettings settings = GeneralSettings.getInstance();

    settings.setReopenLastProject(myComponent.myChkReopenLastProject.isSelected());
    settings.setSyncOnFrameActivation(myComponent.myChkSyncOnFrameActivation.isSelected());
    settings.setSaveOnFrameDeactivation(myComponent.myChkSaveOnFrameDeactivation.isSelected());
    settings.setConfirmExit(myComponent.myConfirmExit.isSelected());
    if (myComponent.myConfirmFrameToOpenCheckBox.isSelected()) {
      settings.setConfirmOpenNewProject(-1);
    }
    // AutoSave in inactive
    settings.setAutoSaveIfInactive(myComponent.myChkAutoSaveIfInactive.isSelected());
    try {
      int newInactiveTimeout = Integer.parseInt(myComponent.myTfInactiveTimeout.getText());
      if (newInactiveTimeout > 0) {
        settings.setInactiveTimeout(newInactiveTimeout);
      }
    }
    catch (NumberFormatException e) {
    }


  }

  public boolean isModified() {
    if (super.isModified()) return true;
    boolean isModified = false;
    GeneralSettings settings = GeneralSettings.getInstance();
    isModified |= settings.isReopenLastProject() != myComponent.myChkReopenLastProject.isSelected();
    isModified |= settings.isSyncOnFrameActivation() != myComponent.myChkSyncOnFrameActivation.isSelected();
    isModified |= settings.isSaveOnFrameDeactivation() != myComponent.myChkSaveOnFrameDeactivation.isSelected();
    isModified |= settings.isAutoSaveIfInactive() != myComponent.myChkAutoSaveIfInactive.isSelected();
    isModified |= settings.isConfirmExit() != myComponent.myConfirmExit.isSelected();

    int openProjectOption = settings.getConfirmOpenNewProject();

    isModified |= (myComponent.myConfirmFrameToOpenCheckBox.isSelected() && openProjectOption >= 0) || (!myComponent.myConfirmFrameToOpenCheckBox.isSelected() == openProjectOption < 0);

    int inactiveTimeout = -1;
    try {
      inactiveTimeout = Integer.parseInt(myComponent.myTfInactiveTimeout.getText());
    }
    catch (NumberFormatException e) {
    }

    isModified |= inactiveTimeout > 0 && settings.getInactiveTimeout() != inactiveTimeout;

    return isModified;
  }

  public JComponent createComponent() {
//    optionGroup.add(getDiffOptions().getPanel());
    myComponent = new MyComponent();

    myComponent.myChkAutoSaveIfInactive.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        myComponent.myTfInactiveTimeout.setEditable(myComponent.myChkAutoSaveIfInactive.isSelected());
      }
    });

    List<SearchableConfigurable> list = getConfigurables();
    if (!list.isEmpty()) {
      myComponent.myPluginOptionsPanel.setLayout(new GridLayout(list.size(), 1));
      for (Configurable c : list) {
        myComponent.myPluginOptionsPanel.add(c.createComponent());
      }
    }

    return myComponent.myPanel;
  }

  public String getDisplayName() {
    return IdeBundle.message("title.general");
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableGeneral.png");
  }

  public void reset() {
    super.reset();
    GeneralSettings settings = GeneralSettings.getInstance();
    myComponent.myChkReopenLastProject.setSelected(settings.isReopenLastProject());
    myComponent.myChkSyncOnFrameActivation.setSelected(settings.isSyncOnFrameActivation());
    myComponent.myChkSaveOnFrameDeactivation.setSelected(settings.isSaveOnFrameDeactivation());

    myComponent.myChkAutoSaveIfInactive.setSelected(settings.isAutoSaveIfInactive());
    myComponent.myTfInactiveTimeout.setText(Integer.toString(settings.getInactiveTimeout()));
    myComponent.myTfInactiveTimeout.setEditable(settings.isAutoSaveIfInactive());
    myComponent.myConfirmExit.setSelected(settings.isConfirmExit());
    myComponent.myConfirmFrameToOpenCheckBox.setSelected(settings.getConfirmOpenNewProject() < 0);
  }

  public void disposeUIResources() {
    super.disposeUIResources();
    myComponent = null;
  }

  @NotNull
  public String getHelpTopic() {
    return "preferences.general";
  }



  private static class MyComponent {
    JPanel myPanel;

    private JCheckBox myChkReopenLastProject;
    private JCheckBox myChkSyncOnFrameActivation;
    private JCheckBox myChkSaveOnFrameDeactivation;
    private JCheckBox myChkAutoSaveIfInactive;
    private JTextField myTfInactiveTimeout;
    public JCheckBox myConfirmExit;
    private JPanel myPluginOptionsPanel;
    private JCheckBox myConfirmFrameToOpenCheckBox;


    public MyComponent() {
    }
  }

  public String getId() {
    return getHelpTopic();
  }

  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }

  protected List<SearchableConfigurable> createConfigurables() {
    return AbstractConfigurableEP.createConfigurables(EP_NAME);
  }
}