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
<<<<<<< HEAD
<<<<<<< HEAD
=======
import com.intellij.openapi.ui.MessageType;
>>>>>>> 38a9775... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
=======
>>>>>>> 5fd2c47... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
<<<<<<< HEAD
<<<<<<< HEAD
import com.intellij.util.ui.GridBag;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
=======
import com.intellij.ui.components.JBRadioButton;
=======
>>>>>>> 5fd2c47... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
import com.intellij.util.ui.GridBag;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
<<<<<<< HEAD
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
>>>>>>> 38a9775... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
=======
>>>>>>> 5fd2c47... IDEA-104500 Gradle: Allow to reuse common logic for other external systems

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

<<<<<<< HEAD
<<<<<<< HEAD
  @NotNull private final String myDisplayName;
  
  @Nullable private final Project myProject;

  @NotNull private JComponent                myComponent;
  @NotNull private JLabel                    myLinkedExternalProjectLabel;
  @NotNull private TextFieldWithBrowseButton myLinkedExternalProjectPathField;
  @NotNull private JBCheckBox                myUseAutoImportBox;

  private final boolean myTestMode;
=======
  @NotNull private final String                    myDisplayName;
  @NotNull private final JLabel                    myLinkedExternalProjectLabel;
  @NotNull private final JComponent                myComponent;
  @NotNull private final TextFieldWithBrowseButton myLinkedExternalProjectPathField;

  @Nullable private final Project myProject;

>>>>>>> 38a9775... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
=======
  @NotNull private final String myDisplayName;
  
  @Nullable private final Project myProject;

  @NotNull private JComponent                myComponent;
  @NotNull private JLabel                    myLinkedExternalProjectLabel;
  @NotNull private TextFieldWithBrowseButton myLinkedExternalProjectPathField;
  @NotNull private JBCheckBox                myUseAutoImportBox;

  private final boolean myTestMode;
>>>>>>> 5fd2c47... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
  private boolean myAlwaysShowLinkedProjectControls;

  @SuppressWarnings("AbstractMethodCallInConstructor")
  protected AbstractExternalProjectConfigurable(@Nullable Project project, @NotNull ProjectSystemId externalSystemId, boolean testMode) {
    myProject = project;
<<<<<<< HEAD
<<<<<<< HEAD
    myTestMode = testMode;
    myDisplayName = getSystemName(externalSystemId);
    myLinkedExternalProjectLabel = new JBLabel(ExternalSystemBundle.message("settings.label.select.project", myDisplayName));
    myLinkedExternalProjectPathField = initLinkedGradleProjectPathControl(testMode);
    myUseAutoImportBox = new JBCheckBox(ExternalSystemBundle.message("settings.label.use.auto.import"));
=======
=======
    myTestMode = testMode;
>>>>>>> 5fd2c47... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
    myDisplayName = getSystemName(externalSystemId);
    myLinkedExternalProjectLabel = new JBLabel(ExternalSystemBundle.message("settings.label.select.project", myDisplayName));
    myLinkedExternalProjectPathField = initLinkedGradleProjectPathControl(testMode);
<<<<<<< HEAD
    myComponent.add(myLinkedExternalProjectLabel, getLabelConstraints());
    myComponent.add(myLinkedExternalProjectPathField, getFillLineConstraints());
>>>>>>> 38a9775... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
=======
    myUseAutoImportBox = new JBCheckBox(ExternalSystemBundle.message("settings.label.use.auto.import"));
>>>>>>> 5fd2c47... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
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

<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 5fd2c47... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
  @Nullable
  public String getLinkedExternalProjectPath() {
    return myLinkedExternalProjectPathField.getText();
  }
  
  public void setLinkedExternalProjectPath(@NotNull String path) {
    myLinkedExternalProjectPathField.setText(path);
  }

  public boolean isUseAutoImport() {
    return myUseAutoImportBox.isSelected();
  }

  @NotNull
  public JBCheckBox getUseAutoImportBox() {
    return myUseAutoImportBox;
  }

  // TODO den add doc
  @Nullable
  public abstract ValidationError validate();
  
<<<<<<< HEAD
=======
>>>>>>> 38a9775... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
=======
>>>>>>> 5fd2c47... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
  // TODO den add doc
  @NotNull
  protected abstract JComponent buildContent(boolean testMode);

<<<<<<< HEAD
<<<<<<< HEAD
  protected abstract void fillContent(@NotNull JComponent content);

=======
>>>>>>> 38a9775... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
=======
  protected abstract void fillContent(@NotNull JComponent content);

>>>>>>> 5fd2c47... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
  @NotNull
  protected abstract FileChooserDescriptor getLinkedProjectConfigDescriptor();

  @NotNull
<<<<<<< HEAD
<<<<<<< HEAD
  protected abstract S getSettings(@NotNull Project project);

  @NotNull
=======
>>>>>>> 38a9775... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
=======
  protected abstract S getSettings(@NotNull Project project);

  @NotNull
>>>>>>> 5fd2c47... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
  private TextFieldWithBrowseButton initLinkedGradleProjectPathControl(boolean testMode) {
    TextFieldWithBrowseButton result = new TextFieldWithBrowseButton();

    FileChooserDescriptor fileChooserDescriptor = testMode ? new FileChooserDescriptor(true, false, false, false, false, false)
                                                           : getLinkedProjectConfigDescriptor();

<<<<<<< HEAD
<<<<<<< HEAD
    result.addBrowseFolderListener(
=======
    myLinkedExternalProjectPathField.addBrowseFolderListener(
>>>>>>> 38a9775... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
=======
    result.addBrowseFolderListener(
>>>>>>> 5fd2c47... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
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
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 5fd2c47... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
    if (myComponent == null) {
      myComponent = buildContent(myTestMode);
      myComponent.add(myLinkedExternalProjectLabel, getLabelConstraints());
      myComponent.add(myLinkedExternalProjectPathField, getFillLineConstraints());
      myComponent.add(myUseAutoImportBox, getFillLineConstraints());
      fillContent(myComponent);
      myComponent.add(Box.createVerticalGlue(), new GridBag().weightx(1).weighty(1).fillCell().coverLine());
    }
<<<<<<< HEAD
=======
>>>>>>> 38a9775... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
=======
>>>>>>> 5fd2c47... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
    return myComponent;
  }

  public boolean isAlwaysShowLinkedProjectControls() {
    return myAlwaysShowLinkedProjectControls;
  }
<<<<<<< HEAD
<<<<<<< HEAD
  
  public void setAlwaysShowLinkedProjectControls(boolean alwaysShowLinkedProjectControls) {
    myAlwaysShowLinkedProjectControls = alwaysShowLinkedProjectControls;
  }
  
=======

  public void setAlwaysShowLinkedProjectControls(boolean alwaysShowLinkedProjectControls) {
    myAlwaysShowLinkedProjectControls = alwaysShowLinkedProjectControls;
  }

>>>>>>> 38a9775... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
=======
  
  public void setAlwaysShowLinkedProjectControls(boolean alwaysShowLinkedProjectControls) {
    myAlwaysShowLinkedProjectControls = alwaysShowLinkedProjectControls;
  }
  
>>>>>>> 5fd2c47... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
  @Override
  public boolean isModified() {
    if (myProject == null) {
      return false;
    }

<<<<<<< HEAD
<<<<<<< HEAD
    S settings = getSettings(myProject);

    if (!Comparing.equal(normalizePath(myLinkedExternalProjectPathField.getText()), normalizePath(settings.getLinkedExternalProjectPath()))) {
=======
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
>>>>>>> 38a9775... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
=======
    S settings = getSettings(myProject);

    if (!Comparing.equal(normalizePath(myLinkedExternalProjectPathField.getText()), normalizePath(settings.getLinkedExternalProjectPath()))) {
>>>>>>> 5fd2c47... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
      return true;
    }

    if (myUseAutoImportBox.isSelected() != settings.isUseAutoImport()) {
      return true;
    }
    
<<<<<<< HEAD
<<<<<<< HEAD
    return isExtraSettingModified();
  }

  protected abstract boolean isExtraSettingModified();

  @Nullable
  protected static String normalizePath(@Nullable String s) {
=======
    return false;
=======
    return isExtraSettingModified();
>>>>>>> 5fd2c47... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
  }

  protected abstract boolean isExtraSettingModified();

  @Nullable
<<<<<<< HEAD
  private static String normalizePath(@Nullable String s) {
>>>>>>> 38a9775... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
=======
  protected static String normalizePath(@Nullable String s) {
>>>>>>> 5fd2c47... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
    return StringUtil.isEmpty(s) ? null : s;
  }

  @Override
  public void apply() {
    if (myProject == null) {
      return;
    }

<<<<<<< HEAD
<<<<<<< HEAD
    String linkedProjectPath = myLinkedExternalProjectPathField.getText();
    boolean useAutoImport = myUseAutoImportBox.isSelected();
    doApply(linkedProjectPath, useAutoImport);
  }

  protected void doApply(@NotNull String linkedExternalProjectPath, boolean useAutoImport) {
    if (myProject == null) {
      return;
    }
    getSettings(myProject).setLinkedExternalProjectPath(linkedExternalProjectPath);
    getSettings(myProject).setUseAutoImport(useAutoImport);
  }

  @Nullable
  protected static String getPathToUse(boolean modifiedByUser, @Nullable String settingsPath, @Nullable String uiPath) {
=======
    GradleSettings settings = myHelper.getSettings(myProject);

=======
>>>>>>> 5fd2c47... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
    String linkedProjectPath = myLinkedExternalProjectPathField.getText();
    boolean useAutoImport = myUseAutoImportBox.isSelected();
    doApply(linkedProjectPath, useAutoImport);
  }

  protected void doApply(@NotNull String linkedExternalProjectPath, boolean useAutoImport) {
    if (myProject == null) {
      return;
    }
    getSettings(myProject).setLinkedExternalProjectPath(linkedExternalProjectPath);
    getSettings(myProject).setUseAutoImport(useAutoImport);
  }

  @Nullable
<<<<<<< HEAD
  private static String getPathToUse(boolean modifiedByUser, @Nullable String settingsPath, @Nullable String uiPath) {
>>>>>>> 38a9775... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
=======
  protected static String getPathToUse(boolean modifiedByUser, @Nullable String settingsPath, @Nullable String uiPath) {
>>>>>>> 5fd2c47... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
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

<<<<<<< HEAD
<<<<<<< HEAD
  @Override
  public void reset() {
    Project project = myProject;
    if (myProject == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }

    S settings = getSettings(project);
    String linkedExternalProjectPath = myLinkedExternalProjectPathField.getText();
    if (StringUtil.isEmpty(linkedExternalProjectPath)) {
      linkedExternalProjectPath = settings.getLinkedExternalProjectPath();
    }
    myLinkedExternalProjectLabel.setVisible(myAlwaysShowLinkedProjectControls || !project.isDefault());
    myLinkedExternalProjectPathField.setVisible(myAlwaysShowLinkedProjectControls || !project.isDefault());
    if (linkedExternalProjectPath != null) {
      myLinkedExternalProjectPathField.setText(linkedExternalProjectPath);
    }

    myUseAutoImportBox.setSelected(settings.isUseAutoImport());
    doReset();
  }
  
  protected abstract void doReset();
=======
  private boolean isValidGradleHome(@Nullable String path) {
    if (StringUtil.isEmpty(path)) {
      return false;
    }
    assert path != null;
    return myHelper.isGradleSdkHome(new File(path));
  }
  
=======
>>>>>>> 5fd2c47... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
  @Override
  public void reset() {
    Project project = myProject;
    if (myProject == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }

    S settings = getSettings(project);
    String linkedExternalProjectPath = myLinkedExternalProjectPathField.getText();
    if (StringUtil.isEmpty(linkedExternalProjectPath)) {
      linkedExternalProjectPath = settings.getLinkedExternalProjectPath();
    }
    myLinkedExternalProjectLabel.setVisible(myAlwaysShowLinkedProjectControls || !project.isDefault());
    myLinkedExternalProjectPathField.setVisible(myAlwaysShowLinkedProjectControls || !project.isDefault());
    if (linkedExternalProjectPath != null) {
      myLinkedExternalProjectPathField.setText(linkedExternalProjectPath);
    }

    myUseAutoImportBox.setSelected(settings.isUseAutoImport());
    doReset();
  }
<<<<<<< HEAD
>>>>>>> 38a9775... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
=======
  
  protected abstract void doReset();
>>>>>>> 5fd2c47... IDEA-104500 Gradle: Allow to reuse common logic for other external systems

  protected static void useColorForPath(@NotNull LocationSettingType type, @NotNull TextFieldWithBrowseButton pathControl) {
    Color c = type == LocationSettingType.DEDUCED ? UIManager.getColor("TextField.inactiveForeground")
                                                  : UIManager.getColor("TextField.foreground");
    pathControl.getTextField().setForeground(c);
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public void disposeUIResources() {
    myComponent = null;
<<<<<<< HEAD
<<<<<<< HEAD
    myLinkedExternalProjectLabel = null;
    myLinkedExternalProjectPathField = null;
    myUseAutoImportBox = null;
  }
  
  public static class ValidationError {
    @NotNull public final String     message;
    @NotNull public final JComponent problemHolder;

    public ValidationError(@NotNull String message, @NotNull JComponent problemHolder) {
      this.message = message;
      this.problemHolder = problemHolder;
    }
  }
=======
    myGradleHomePathField = null;
    myUseWrapperButton = null;
    myUseLocalDistributionButton = null;
=======
    myLinkedExternalProjectLabel = null;
    myLinkedExternalProjectPathField = null;
    myUseAutoImportBox = null;
>>>>>>> 5fd2c47... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
  }
  
  public static class ValidationError {
    @NotNull public final String     message;
    @NotNull public final JComponent problemHolder;

    public ValidationError(@NotNull String message, @NotNull JComponent problemHolder) {
      this.message = message;
      this.problemHolder = problemHolder;
    }
  }
<<<<<<< HEAD

  

  
>>>>>>> 38a9775... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
=======
>>>>>>> 5fd2c47... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
}
