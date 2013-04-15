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
package com.intellij.openapi.externalSystem.service.settings;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.settings.LocationSettingType;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListener;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.ui.GridBag;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

/**
 * // TODO den add doc
 * Allows to configure gradle settings.
 * <p/>
 * Basically, it has two modes:
 * <pre>
 * <ul>
 *   <li>no information about linked gradle project is available (e.g. gradle settings are opened from welcome screen);</li>
 *   <li>information about linked gradle project is available;</li>
 * </ul>
 * </pre>
 * The difference is in how we handle
 * <a href="http://www.gradle.org/docs/current/userguide/userguide_single.html#gradle_wrapper">gradle wrapper</a> settings - we
 * represent settings like 'use gradle wrapper whenever possible' at the former case and ask to explicitly define whether gradle
 * wrapper or particular local distribution should be used at the latest one.
 * 
 * @author peter
 */
public abstract class AbstractExternalProjectConfigurable
  <L extends ExternalSystemSettingsListener, S extends AbstractExternalSystemSettings<L, S>>
  implements SearchableConfigurable, Configurable.NoScroll
{

  @NotNull private final String                    myDisplayName;
  @NotNull private final JLabel                    myLinkedExternalProjectLabel;
  @NotNull private final JComponent                myComponent;
  @NotNull private final TextFieldWithBrowseButton myLinkedExternalProjectPathField;

  @Nullable private final Project myProject;

  private boolean myAlwaysShowLinkedProjectControls;

  @SuppressWarnings("AbstractMethodCallInConstructor")
  protected AbstractExternalProjectConfigurable(@Nullable Project project, @NotNull ProjectSystemId externalSystemId, boolean testMode) {
    myProject = project;
    myDisplayName = getSystemName(externalSystemId);
    myLinkedExternalProjectLabel = new JBLabel(ExternalSystemBundle.message("settings.label.select.project", myDisplayName));
    myComponent = buildContent(testMode);
    myLinkedExternalProjectPathField = initLinkedGradleProjectPathControl(testMode);
    myComponent.add(myLinkedExternalProjectLabel, getLabelConstraints());
    myComponent.add(myLinkedExternalProjectPathField, getFillLineConstraints());
  }

  @NotNull
  protected static GridBag getLabelConstraints() {
    return new GridBag().anchor(GridBagConstraints.WEST).weightx(0);
  }

  @NotNull
  protected static GridBag getFillLineConstraints() {
    return new GridBag().weightx(1).coverLine().fillCellHorizontally().anchor(GridBagConstraints.WEST);
  }

  @NotNull
  private static String getSystemName(@NotNull ProjectSystemId externalSystemId) {
    return StringUtil.capitalize(externalSystemId.toString().toLowerCase());
  }

  @NotNull
  public TextFieldWithBrowseButton getLinkedExternalProjectPathField() {
    return myLinkedExternalProjectPathField;
  }

  // TODO den add doc
  @NotNull
  protected abstract JComponent buildContent(boolean testMode);

  @NotNull
  protected abstract FileChooserDescriptor getLinkedProjectConfigDescriptor();

  @NotNull
  private TextFieldWithBrowseButton initLinkedGradleProjectPathControl(boolean testMode) {
    TextFieldWithBrowseButton result = new TextFieldWithBrowseButton();

    FileChooserDescriptor fileChooserDescriptor = testMode ? new FileChooserDescriptor(true, false, false, false, false, false)
                                                           : getLinkedProjectConfigDescriptor();

    myLinkedExternalProjectPathField.addBrowseFolderListener(
      "",
      ExternalSystemBundle.message("settings.label.select.project", myDisplayName),
      myProject,
      fileChooserDescriptor,
      TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT,
      false
    );
    return result;
  }

  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  @Nullable
  public Project getProject() {
    return myProject;
  }

  @NotNull
  @Nls
  @Override
  public String getDisplayName() {
    return myDisplayName;
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public JComponent createComponent() {
    return myComponent;
  }

  public boolean isAlwaysShowLinkedProjectControls() {
    return myAlwaysShowLinkedProjectControls;
  }

  public void setAlwaysShowLinkedProjectControls(boolean alwaysShowLinkedProjectControls) {
    myAlwaysShowLinkedProjectControls = alwaysShowLinkedProjectControls;
  }

  @Override
  public boolean isModified() {
    if (myProject == null) {
      return false;
    }

    GradleSettings settings = myHelper.getSettings(myProject);

    if (!Comparing.equal(normalizePath(myLinkedExternalProjectPathField.getText()), normalizePath(settings.getLinkedProjectPath()))) {
      return true;
    }
    
    boolean preferLocalToWrapper = settings.isPreferLocalInstallationToWrapper();
    if (myUseWrapperButton.isSelected() == preferLocalToWrapper) {
      return true;
    }

    if (myGradleHomeModifiedByUser &&
        !Comparing.equal(normalizePath(myGradleHomePathField.getText()), normalizePath(settings.getGradleHome())))
    {
      return true;
    }

    if (myServiceDirectoryModifiedByUser &&
        !Comparing.equal(normalizePath(myServiceDirectoryPathField.getText()), normalizePath(settings.getServiceDirectoryPath())))
    {
      return true;
    }

    if (myUseAutoImportBox.isSelected() != settings.isUseAutoImport()) {
      return true;
    }
    
    return false;
  }

  @Nullable
  private static String normalizePath(@Nullable String s) {
    return StringUtil.isEmpty(s) ? null : s;
  }

  @Override
  public void apply() {
    if (myProject == null) {
      return;
    }

    GradleSettings settings = myHelper.getSettings(myProject);

    String linkedProjectPath = myLinkedExternalProjectPathField.getText();
    final String gradleHomePath = getPathToUse(myGradleHomeModifiedByUser, settings.getGradleHome(), myGradleHomePathField.getText());
    final String serviceDirPath = getPathToUse(myServiceDirectoryModifiedByUser,
                                               settings.getServiceDirectoryPath(),
                                               myServiceDirectoryPathField.getText());
    
    boolean preferLocalToWrapper = myUseLocalDistributionButton.isSelected();
    boolean useAutoImport = myUseAutoImportBox.isSelected();
    myHelper.applySettings(linkedProjectPath, gradleHomePath, preferLocalToWrapper, useAutoImport, serviceDirPath, myProject);

    Project defaultProject = myHelper.getDefaultProject();
    if (myProject != defaultProject) {
      myHelper.applyPreferLocalInstallationToWrapper(preferLocalToWrapper, defaultProject);
    }

    if (isValidGradleHome(gradleHomePath)) {
      if (myGradleHomeModifiedByUser) {
        myGradleHomeSettingType = GradleHomeSettingType.EXPLICIT_CORRECT;
        // There is a possible case that user defines gradle home for particular open project. We want to apply that value
        // to the default project as well if it's still non-defined.
        if (defaultProject != myProject && !isValidGradleHome(GradleSettings.getInstance(defaultProject).getGradleHome())) {
          // TODO den implement
//          GradleSettings.applyGradleHome(gradleHomePath, defaultProject);
        }
      }
      else {
        myGradleHomeSettingType = GradleHomeSettingType.DEDUCED;
      }
    }
    else if (preferLocalToWrapper) {
      if (StringUtil.isEmpty(gradleHomePath)) {
        myGradleHomeSettingType = GradleHomeSettingType.UNKNOWN;
      }
      else {
        myGradleHomeSettingType = GradleHomeSettingType.EXPLICIT_INCORRECT;
        myHelper.showBalloon(MessageType.ERROR, myGradleHomeSettingType, 0);
      }
    }
  }

  @Nullable
  private static String getPathToUse(boolean modifiedByUser, @Nullable String settingsPath, @Nullable String uiPath) {
    if (modifiedByUser) {
      return StringUtil.isEmpty(uiPath) ? null : uiPath;
    }
    else {
      // There are two possible cases:
      //   *) the path is initially set from settings and hasn't been modified; 
      //   *) the path is undefined at the settings and has been deduced;
      if (Comparing.equal(normalizePath(settingsPath), normalizePath(uiPath))) {
        return settingsPath;
      }
      else {
        return null;
      }
    }
  }

  private boolean isValidGradleHome(@Nullable String path) {
    if (StringUtil.isEmpty(path)) {
      return false;
    }
    assert path != null;
    return myHelper.isGradleSdkHome(new File(path));
  }
  
  @Override
  public void reset() {
    if (myProject == null) {
      return;
    }
    
    // Process gradle wrapper/local distribution settings.
    // There are the following possible cases:
    //   1. Default project or non-default project with no linked gradle project - 'use gradle wrapper whenever possible' check box
    //      should be shown;
    //   2. Non-default project with linked gradle project:
    //      2.1. Gradle wrapper is configured for the target project - radio buttons on whether to use wrapper or particular local gradle
    //           distribution should be show;
    //      2.2. Gradle wrapper is not configured for the target project - radio buttons should be shown and 
    //           'use gradle wrapper' option should be disabled;
    useColorForPath(PathColor.NORMAL, myGradleHomePathField);
    useColorForPath(PathColor.NORMAL, myServiceDirectoryPathField);
    GradleSettings settings = myHelper.getSettings(myProject);
    String linkedProjectPath = myLinkedExternalProjectPathField.getText();
    if (StringUtil.isEmpty(linkedProjectPath)) {
      linkedProjectPath = settings.getLinkedProjectPath();
    }
    myLinkedExternalProjectLabel.setVisible(myAlwaysShowLinkedProjectControls || !myProject.isDefault());
    myLinkedExternalProjectPathField.setVisible(myAlwaysShowLinkedProjectControls || !myProject.isDefault());
    if (linkedProjectPath != null) {
      myLinkedExternalProjectPathField.setText(linkedProjectPath);
    }
    
    myUseWrapperButton.setVisible(myAlwaysShowLinkedProjectControls || (!myProject.isDefault() && linkedProjectPath != null));
    myUseLocalDistributionButton.setVisible(myAlwaysShowLinkedProjectControls || (!myProject.isDefault() && linkedProjectPath != null));
    if (myAlwaysShowLinkedProjectControls && linkedProjectPath == null) {
      myUseWrapperButton.setEnabled(false);
      myUseLocalDistributionButton.setSelected(true);
    }
    else if (linkedProjectPath != null) {
      if (myHelper.isGradleWrapperDefined(linkedProjectPath)) {
        myUseWrapperButton.setEnabled(true);
        myUseWrapperButton.setText(GradleBundle.message("gradle.config.text.use.wrapper"));
        if (myProject.isDefault() || !settings.isPreferLocalInstallationToWrapper()) {
          myUseWrapperButton.setSelected(true);
          myGradleHomePathField.setEnabled(false);
        }
        else {
          myUseLocalDistributionButton.setSelected(true);
          myGradleHomePathField.setEnabled(true);
        }
      }
      else {
        myUseWrapperButton.setText(GradleBundle.message("gradle.config.text.use.wrapper.disabled"));
        myUseWrapperButton.setEnabled(false);
        myUseLocalDistributionButton.setSelected(true);
      }
    }
    
    String localDistributionPath = settings.getGradleHome();
    if (StringUtil.isEmpty(localDistributionPath)) {
      myGradleHomeSettingType = GradleHomeSettingType.UNKNOWN;
      deduceGradleHomeIfPossible();
    }
    else {
      myGradleHomeSettingType = myHelper.isGradleSdkHome(new File(localDistributionPath)) ?
                                GradleHomeSettingType.EXPLICIT_CORRECT :
                                GradleHomeSettingType.EXPLICIT_INCORRECT;
      myAlarm.cancelAllRequests();
      if (myGradleHomeSettingType == GradleHomeSettingType.EXPLICIT_INCORRECT && settings.isPreferLocalInstallationToWrapper()) {
        myHelper.showBalloon(MessageType.ERROR, myGradleHomeSettingType, 0);
      }
      myGradleHomePathField.setText(localDistributionPath);
    }

    String serviceDirectoryPath = settings.getServiceDirectoryPath();
    if (StringUtil.isEmpty(serviceDirectoryPath)) {
      deduceServiceDirectoryIfPossible();
    }
    else {
      myServiceDirectoryPathField.setText(serviceDirectoryPath);
      useColorForPath(PathColor.NORMAL, myServiceDirectoryPathField);
    }
    
    myUseAutoImportBox.setSelected(settings.isUseAutoImport());
  }

  protected static void useColorForPath(@NotNull LocationSettingType type, @NotNull TextFieldWithBrowseButton pathControl) {
    Color c = type == LocationSettingType.DEDUCED ? UIManager.getColor("TextField.inactiveForeground")
                                                  : UIManager.getColor("TextField.foreground");
    pathControl.getTextField().setForeground(c);
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public void disposeUIResources() {
    myComponent = null;
    myGradleHomePathField = null;
    myUseWrapperButton = null;
    myUseLocalDistributionButton = null;
  }

  @SuppressWarnings("UseOfArchaicSystemPropertyAccessors")
  @NotNull
  public GradleLocationSettingType getCurrentGradleHomeSettingType() {
    String path = myGradleHomePathField.getText();
    if (GradleEnvironment.DEBUG_GRADLE_HOME_PROCESSING) {
      GradleLog.LOG.info(String.format("Checking 'gradle home' status. Manually entered value is '%s'", path));
    }
    if (path == null || StringUtil.isEmpty(path.trim())) {
      return GradleHomeSettingType.UNKNOWN;
    }
    if (isModified()) {
      return myHelper.isGradleSdkHome(new File(path)) ? GradleHomeSettingType.EXPLICIT_CORRECT
                                                      : GradleHomeSettingType.EXPLICIT_INCORRECT;
    }
    return myGradleHomeSettingType;
  }

  

  
}
