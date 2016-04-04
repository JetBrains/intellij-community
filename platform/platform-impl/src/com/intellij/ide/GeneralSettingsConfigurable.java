/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.ui.components.JBRadioButton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.List;

/**
 * To provide additional options in General section register implementation of {@link com.intellij.openapi.options.SearchableConfigurable} in the plugin.xml:
 * <p/>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;<br>
 * &nbsp;&nbsp;&lt;generalOptionsProvider instance="class-name"/&gt;<br>
 * &lt;/extensions&gt;
 * <p>
 * A new instance of the specified class will be created each time then the Settings dialog is opened
 */
public class GeneralSettingsConfigurable extends CompositeConfigurable<SearchableConfigurable> implements SearchableConfigurable, Configurable.NoScroll {
  private static final ExtensionPointName<GeneralSettingsConfigurableEP> EP_NAME = ExtensionPointName.create("com.intellij.generalOptionsProvider");
  
  private MyComponent myComponent;

  public GeneralSettingsConfigurable() {
    myComponent = new MyComponent();
  }

  public void apply() throws ConfigurationException {
    super.apply();
    GeneralSettings settings = GeneralSettings.getInstance();

    settings.setReopenLastProject(myComponent.myChkReopenLastProject.isSelected());
    settings.setSyncOnFrameActivation(myComponent.myChkSyncOnFrameActivation.isSelected());
    settings.setSaveOnFrameDeactivation(myComponent.myChkSaveOnFrameDeactivation.isSelected());
    settings.setConfirmExit(myComponent.myConfirmExit.isSelected());
    settings.setConfirmOpenNewProject(getConfirmOpenNewProject());

    settings.setAutoSaveIfInactive(myComponent.myChkAutoSaveIfInactive.isSelected());
    try {
      int newInactiveTimeout = Integer.parseInt(myComponent.myTfInactiveTimeout.getText());
      if (newInactiveTimeout > 0) {
        settings.setInactiveTimeout(newInactiveTimeout);
      }
    }
    catch (NumberFormatException ignored) { }
    settings.setUseSafeWrite(myComponent.myChkUseSafeWrite.isSelected());
  }

  private int getConfirmOpenNewProject() {
    if (myComponent.myConfirmWindowToOpenProject.isSelected()) {
      return GeneralSettings.OPEN_PROJECT_ASK;
    }
    else if (myComponent.myOpenProjectInNewWindow.isSelected()) {
      return GeneralSettings.OPEN_PROJECT_NEW_WINDOW;
    }
    else {
      return GeneralSettings.OPEN_PROJECT_SAME_WINDOW;
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
    isModified |= settings.getConfirmOpenNewProject() != getConfirmOpenNewProject();

    int inactiveTimeout = -1;
    try {
      inactiveTimeout = Integer.parseInt(myComponent.myTfInactiveTimeout.getText());
    }
    catch (NumberFormatException ignored) { }
    isModified |= inactiveTimeout > 0 && settings.getInactiveTimeout() != inactiveTimeout;

    isModified |= settings.isUseSafeWrite() != myComponent.myChkUseSafeWrite.isSelected();

    return isModified;
  }

  public JComponent createComponent() {
    if (myComponent == null) {
      myComponent = new MyComponent();
    }

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

  public void reset() {
    super.reset();
    GeneralSettings settings = GeneralSettings.getInstance();
    myComponent.myChkReopenLastProject.setSelected(settings.isReopenLastProject());
    myComponent.myChkSyncOnFrameActivation.setSelected(settings.isSyncOnFrameActivation());
    myComponent.myChkSaveOnFrameDeactivation.setSelected(settings.isSaveOnFrameDeactivation());
    myComponent.myChkAutoSaveIfInactive.setSelected(settings.isAutoSaveIfInactive());
    myComponent.myTfInactiveTimeout.setText(Integer.toString(settings.getInactiveTimeout()));
    myComponent.myTfInactiveTimeout.setEditable(settings.isAutoSaveIfInactive());
    myComponent.myChkUseSafeWrite.setSelected(settings.isUseSafeWrite());
    myComponent.myConfirmExit.setSelected(settings.isConfirmExit());
    switch (settings.getConfirmOpenNewProject()) {
      case GeneralSettings.OPEN_PROJECT_ASK:
        myComponent.myConfirmWindowToOpenProject.setSelected(true);
        break;
      case GeneralSettings.OPEN_PROJECT_NEW_WINDOW:
        myComponent.myOpenProjectInNewWindow.setSelected(true);
        break;
      case GeneralSettings.OPEN_PROJECT_SAME_WINDOW:
        myComponent.myOpenProjectInSameWindow.setSelected(true);
        break;
    }
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
    private JPanel myPanel;
    private JCheckBox myChkReopenLastProject;
    private JCheckBox myChkSyncOnFrameActivation;
    private JCheckBox myChkSaveOnFrameDeactivation;
    private JCheckBox myChkAutoSaveIfInactive;
    private JTextField myTfInactiveTimeout;
    private JCheckBox myChkUseSafeWrite;
    private JCheckBox myConfirmExit;
    private JPanel myPluginOptionsPanel;
    private JBRadioButton myOpenProjectInNewWindow;
    private JBRadioButton myOpenProjectInSameWindow;
    private JBRadioButton myConfirmWindowToOpenProject;

    public MyComponent() { }
  }

  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }

  protected List<SearchableConfigurable> createConfigurables() {
    return ConfigurableWrapper.createConfigurables(EP_NAME);
  }
}
