/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.PathChooserDialog;
import com.intellij.openapi.options.CompositeConfigurable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.IdeUICustomization;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.List;

/**
 * To provide additional options in General section register implementation of {@link SearchableConfigurable} in the plugin.xml:
 * <p/>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;<br>
 * &nbsp;&nbsp;&lt;generalOptionsProvider instance="class-name"/&gt;<br>
 * &lt;/extensions&gt;
 * <p>
 * A new instance of the specified class will be created each time then the Settings dialog is opened
 */
public class GeneralSettingsConfigurable extends CompositeConfigurable<SearchableConfigurable> implements SearchableConfigurable {
  private static final ExtensionPointName<GeneralSettingsConfigurableEP> EP_NAME = ExtensionPointName.create("com.intellij.generalOptionsProvider");
  
  private MyComponent myComponent;

  public GeneralSettingsConfigurable() {
    myComponent = new MyComponent();
  }

  @Override
  public void apply() throws ConfigurationException {
    super.apply();
    GeneralSettings settings = GeneralSettings.getInstance();

    settings.setReopenLastProject(myComponent.myChkReopenLastProject.isSelected());
    settings.setSupportScreenReaders(myComponent.myChkSupportScreenReaders.isSelected());
    settings.setSyncOnFrameActivation(myComponent.myChkSyncOnFrameActivation.isSelected());
    settings.setSaveOnFrameDeactivation(myComponent.myChkSaveOnFrameDeactivation.isSelected());
    settings.setConfirmExit(myComponent.myConfirmExit.isSelected());
    settings.setShowWelcomeScreen(myComponent.myShowWelcomeScreen.isSelected());
    settings.setConfirmOpenNewProject(getConfirmOpenNewProject());
    settings.setProcessCloseConfirmation(getProcessCloseConfirmation());

    settings.setAutoSaveIfInactive(myComponent.myChkAutoSaveIfInactive.isSelected());
    try {
      settings.setInactiveTimeout(Integer.parseInt(myComponent.myTfInactiveTimeout.getText()));//See range validation inside settings
    }
    catch (NumberFormatException ignored) { }
    settings.setUseSafeWrite(myComponent.myChkUseSafeWrite.isSelected());
    settings.setDefaultProjectDirectory(myComponent.myProjectDirectoryTextField.getText());
  }

  private GeneralSettings.ProcessCloseConfirmation getProcessCloseConfirmation() {
    if (myComponent.myTerminateProcessJBRadioButton.isSelected()) {
      return GeneralSettings.ProcessCloseConfirmation.TERMINATE;
    }
    else if (myComponent.myDisconnectJBRadioButton.isSelected()) {
      return GeneralSettings.ProcessCloseConfirmation.DISCONNECT;
    }
    else {
      return GeneralSettings.ProcessCloseConfirmation.ASK;
    }
  }

  @GeneralSettings.OpenNewProjectOption
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

  @Override
  public boolean isModified() {
    if (super.isModified()) return true;
    GeneralSettings settings = GeneralSettings.getInstance();
    boolean isModified = settings.isReopenLastProject() != myComponent.myChkReopenLastProject.isSelected();
    isModified |= settings.isSupportScreenReaders() != myComponent.myChkSupportScreenReaders.isSelected();
    isModified |= settings.isSyncOnFrameActivation() != myComponent.myChkSyncOnFrameActivation.isSelected();
    isModified |= settings.isSaveOnFrameDeactivation() != myComponent.myChkSaveOnFrameDeactivation.isSelected();
    isModified |= settings.isAutoSaveIfInactive() != myComponent.myChkAutoSaveIfInactive.isSelected();
    isModified |= settings.isConfirmExit() != myComponent.myConfirmExit.isSelected();
    isModified |= settings.isShowWelcomeScreen() != myComponent.myShowWelcomeScreen.isSelected();
    isModified |= settings.getConfirmOpenNewProject() != getConfirmOpenNewProject();
    isModified |= settings.getProcessCloseConfirmation() != getProcessCloseConfirmation();
    isModified |= isModified(myComponent.myTfInactiveTimeout, settings.getInactiveTimeout(), GeneralSettings.SAVE_FILES_AFTER_IDLE_SEC);

    isModified |= settings.isUseSafeWrite() != myComponent.myChkUseSafeWrite.isSelected();
    isModified |= !settings.getDefaultProjectDirectory().equals(myComponent.myProjectDirectoryTextField.getText());

    return isModified;
  }

  @Override
  public JComponent createComponent() {
    if (myComponent == null) {
      myComponent = new MyComponent();
    }
    myComponent.myShowWelcomeScreen.setVisible(PlatformUtils.isDataGrip());

    myComponent.myChkAutoSaveIfInactive.addChangeListener(
      e -> myComponent.myTfInactiveTimeout.setEditable(myComponent.myChkAutoSaveIfInactive.isSelected()));

    List<SearchableConfigurable> list = getConfigurables();
    if (!list.isEmpty()) {
      myComponent.myPluginOptionsPanel.setLayout(new GridLayout(list.size(), 1));
      for (Configurable c : list) {
        myComponent.myPluginOptionsPanel.add(c.createComponent());
      }
    }

    return myComponent.myPanel;
  }

  @Override
  public String getDisplayName() {
    return IdeBundle.message("title.general");
  }

  @Override
  public void reset() {
    super.reset();
    GeneralSettings settings = GeneralSettings.getInstance();
    myComponent.myChkReopenLastProject.setSelected(settings.isReopenLastProject());
    myComponent.myChkSupportScreenReaders.setSelected(settings.isSupportScreenReaders());
    if (GeneralSettings.isSupportScreenReadersOverriden()) {
      myComponent.myChkSupportScreenReaders.setEnabled(false);
      myComponent.myChkSupportScreenReaders.setToolTipText(
        "The option is overriden by the JVM property: \"" + GeneralSettings.SUPPORT_SCREEN_READERS + "\"");
    }
    myComponent.myChkSyncOnFrameActivation.setSelected(settings.isSyncOnFrameActivation());
    myComponent.myChkSaveOnFrameDeactivation.setSelected(settings.isSaveOnFrameDeactivation());
    myComponent.myChkAutoSaveIfInactive.setSelected(settings.isAutoSaveIfInactive());
    myComponent.myTfInactiveTimeout.setText(Integer.toString(settings.getInactiveTimeout()));
    myComponent.myTfInactiveTimeout.setEditable(settings.isAutoSaveIfInactive());
    myComponent.myChkUseSafeWrite.setSelected(settings.isUseSafeWrite());
    myComponent.myConfirmExit.setSelected(settings.isConfirmExit());
    myComponent.myShowWelcomeScreen.setSelected(settings.isShowWelcomeScreen());
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
    switch (settings.getProcessCloseConfirmation()) {
      case TERMINATE:
        myComponent.myTerminateProcessJBRadioButton.setSelected(true);
        break;
      case DISCONNECT:
        myComponent.myDisconnectJBRadioButton.setSelected(true);
        break;
      case ASK:
        myComponent.myAskJBRadioButton.setSelected(true);
        break;
    }
    myComponent.myProjectDirectoryTextField.setText(settings.getDefaultProjectDirectory());
  }

  @Override
  public void disposeUIResources() {
    super.disposeUIResources();
    myComponent = null;
  }

  @Override
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
    private JCheckBox myShowWelcomeScreen;
    private JPanel myPluginOptionsPanel;
    private JBRadioButton myOpenProjectInNewWindow;
    private JBRadioButton myOpenProjectInSameWindow;
    private JBRadioButton myConfirmWindowToOpenProject;
    private JCheckBox myChkSupportScreenReaders;
    private JBRadioButton myTerminateProcessJBRadioButton;
    private JBRadioButton myDisconnectJBRadioButton;
    private JBRadioButton myAskJBRadioButton;
    private TextFieldWithBrowseButton myProjectDirectoryTextField;
    private JPanel myProjectOpeningPanel;

    public MyComponent() {
      String conceptName = IdeUICustomization.getInstance().getProjectConceptName();
      myChkReopenLastProject.setText(IdeBundle.message("checkbox.reopen.last.project.on.startup", conceptName));
      ((TitledBorder) myProjectOpeningPanel.getBorder()).setTitle(IdeBundle.message("border.title.project.opening",
                                                                                    StringUtil.capitalize(conceptName)));
      myOpenProjectInNewWindow.setText(IdeBundle.message("radio.button.open.project.in.the.new.window", conceptName));
      myOpenProjectInSameWindow.setText(IdeBundle.message("radio.button.open.project.in.the.same.window", conceptName));
      myConfirmWindowToOpenProject.setText(IdeBundle.message("radio.button.confirm.window.to.open.project.in", conceptName));
    }

    private void createUIComponents() {
      myProjectDirectoryTextField = new TextFieldWithBrowseButton();
      FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
      descriptor.putUserData(PathChooserDialog.PREFER_LAST_OVER_EXPLICIT, false);
      myProjectDirectoryTextField.addBrowseFolderListener(null, null, null, descriptor);
    }
  }

  @Override
  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  @NotNull
  @Override
  protected List<SearchableConfigurable> createConfigurables() {
    return ConfigurableWrapper.createConfigurables(EP_NAME);
  }
}
