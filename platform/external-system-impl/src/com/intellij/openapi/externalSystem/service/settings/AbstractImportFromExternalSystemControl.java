/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListener;
import com.intellij.openapi.externalSystem.util.*;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.projectImport.ProjectFormatPanel;
import com.intellij.ui.HideableTitledPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

/**
 * A control which knows how to manage settings of external project being imported.
 * 
 * @author Denis Zhdanov
 * @since 4/30/13 2:33 PM
 */
public abstract class AbstractImportFromExternalSystemControl<
  ProjectSettings extends ExternalProjectSettings,
  L extends ExternalSystemSettingsListener<ProjectSettings>,
  SystemSettings extends AbstractExternalSystemSettings<SystemSettings, ProjectSettings, L>>
{
  @NotNull private final SystemSettings  mySystemSettings;
  @NotNull private final ProjectSettings myProjectSettings;

  @NotNull private final PaintAwarePanel           myComponent              = new PaintAwarePanel(new GridBagLayout());
  @NotNull private final TextFieldWithBrowseButton myLinkedProjectPathField = new TextFieldWithBrowseButton();
  @Nullable private final HideableTitledPanel hideableSystemSettingsPanel;
  @NotNull private final ProjectFormatPanel myProjectFormatPanel;

  @NotNull private final  ExternalSystemSettingsControl<ProjectSettings> myProjectSettingsControl;
  @NotNull private final  ProjectSystemId                                myExternalSystemId;
  @Nullable private final ExternalSystemSettingsControl<SystemSettings>  mySystemSettingsControl;

  @Nullable Project myCurrentProject;

  private boolean myShowProjectFormatPanel;
  private final JLabel myProjectFormatLabel;

  protected AbstractImportFromExternalSystemControl(@NotNull ProjectSystemId externalSystemId,
                                                    @NotNull SystemSettings systemSettings,
                                                    @NotNull ProjectSettings projectSettings)
  {
    this(externalSystemId, systemSettings, projectSettings, false);
  }

  @SuppressWarnings("AbstractMethodCallInConstructor")
  protected AbstractImportFromExternalSystemControl(@NotNull ProjectSystemId externalSystemId,
                                                    @NotNull SystemSettings systemSettings,
                                                    @NotNull ProjectSettings projectSettings,
                                                    boolean showProjectFormatPanel)
  {
    myExternalSystemId = externalSystemId;
    mySystemSettings = systemSettings;
    myProjectSettings = projectSettings;
    myProjectSettingsControl = createProjectSettingsControl(projectSettings);
    mySystemSettingsControl = createSystemSettingsControl(systemSettings);
    myShowProjectFormatPanel = showProjectFormatPanel;

    JLabel linkedProjectPathLabel =
      new JLabel(ExternalSystemBundle.message("settings.label.select.project", externalSystemId.getReadableName()));
    ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(externalSystemId);
    assert manager != null;
    FileChooserDescriptor fileChooserDescriptor = manager.getExternalProjectDescriptor();

    myLinkedProjectPathField.addBrowseFolderListener("",
                                                     ExternalSystemBundle
                                                       .message("settings.label.select.project", externalSystemId.getReadableName()),
                                                     null,
                                                     fileChooserDescriptor,
                                                     TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT,
                                                     false);
    myLinkedProjectPathField.getTextField().getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        onLinkedProjectPathChange(myLinkedProjectPathField.getText());
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        onLinkedProjectPathChange(myLinkedProjectPathField.getText());
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        onLinkedProjectPathChange(myLinkedProjectPathField.getText());
      }
    });

    myComponent.add(linkedProjectPathLabel, ExternalSystemUiUtil.getLabelConstraints(0));
    myComponent.add(myLinkedProjectPathField, ExternalSystemUiUtil.getFillLineConstraints(0));
    myProjectSettingsControl.fillUi(myComponent, 0);

    myProjectFormatPanel = new ProjectFormatPanel();
    myProjectFormatLabel = new JLabel(ExternalSystemBundle.message("settings.label.project.format"));
    myComponent.add(myProjectFormatLabel, ExternalSystemUiUtil.getLabelConstraints(0));
    myComponent.add(myProjectFormatPanel.getStorageFormatComboBox(), ExternalSystemUiUtil.getFillLineConstraints(0));

    if (mySystemSettingsControl != null) {
      final PaintAwarePanel mySystemSettingsControlPanel = new PaintAwarePanel();
      mySystemSettingsControl.fillUi(mySystemSettingsControlPanel, 0);

      JPanel panel = new JPanel(new BorderLayout());
      panel.add(mySystemSettingsControlPanel, BorderLayout.CENTER);
      hideableSystemSettingsPanel = new HideableTitledPanel(
        ExternalSystemBundle.message("settings.title.system.settings", myExternalSystemId.getReadableName()), false);
      hideableSystemSettingsPanel.setContentComponent(panel);
      hideableSystemSettingsPanel.setOn(false);
      myComponent.add(hideableSystemSettingsPanel, ExternalSystemUiUtil.getFillLineConstraints(0));
    } else {
      hideableSystemSettingsPanel = null;
    }
    ExternalSystemUiUtil.fillBottom(myComponent);
  }

  /**
   * This control is assumed to be used at least at two circumstances:
   * <pre>
   * <ul>
   *   <li>new ide project is being created on the external project basis;</li>
   *   <li>new ide module(s) is being added to the existing ide project on the external project basis;</li>
   * </ul>
   * </pre>
   * We need to differentiate these situations, for example, we don't want to allow linking an external project to existing ide
   * project if it's already linked.
   * <p/>
   * This property helps us to achieve that - when an ide project is defined, that means that new modules are being imported
   * to that ide project from external project; when this property is <code>null</code> that means that new ide project is being
   * created on the target external project basis.
   * 
   * @param currentProject  current ide project (if any)
   */
  public void setCurrentProject(@Nullable Project currentProject) {
    myCurrentProject = currentProject;
  }

  protected abstract void onLinkedProjectPathChange(@NotNull String path);

  /**
   * Creates a control for managing given project settings.
   *
   * @param settings  target external project settings
   * @return          control for managing given project settings
   */
  @NotNull
  protected abstract ExternalSystemSettingsControl<ProjectSettings> createProjectSettingsControl(@NotNull ProjectSettings settings);

  /**
   * Creates a control for managing given system-level settings (if any).
   *
   * @param settings  target system settings
   * @return          a control for managing given system-level settings;
   *                  <code>null</code> if current external system doesn't have system-level settings (only project-level settings)
   */
  @Nullable
  protected abstract ExternalSystemSettingsControl<SystemSettings> createSystemSettingsControl(@NotNull SystemSettings settings);

  @NotNull
  public JComponent getComponent() {
    return myComponent;
  }

  @NotNull
  public ExternalSystemSettingsControl<ProjectSettings> getProjectSettingsControl() {
    return myProjectSettingsControl;
  }

  @Nullable
  public ExternalSystemSettingsControl<SystemSettings> getSystemSettingsControl() {
    return mySystemSettingsControl;
  }

  public void setLinkedProjectPath(@NotNull String path) {
    myProjectSettings.setExternalProjectPath(path);
    myLinkedProjectPathField.setText(path);
  }

  @NotNull
  public SystemSettings getSystemSettings() {
    return mySystemSettings;
  }

  @NotNull
  public ProjectSettings getProjectSettings() {
    return myProjectSettings;
  }

  public void setShowProjectFormatPanel(boolean showProjectFormatPanel) {
    myShowProjectFormatPanel = showProjectFormatPanel;
  }

  public void reset() {
    myLinkedProjectPathField.setText("");
    myProjectSettingsControl.reset();
    if (mySystemSettingsControl != null) {
      mySystemSettingsControl.reset();
    }
    if (hideableSystemSettingsPanel != null) {
      hideableSystemSettingsPanel.setOn(false);
    }
    myProjectFormatLabel.setVisible(myShowProjectFormatPanel);
    myProjectFormatPanel.setVisible(myShowProjectFormatPanel);
    myProjectFormatPanel.getPanel().setVisible(myShowProjectFormatPanel);
    myProjectFormatPanel.getStorageFormatComboBox().setVisible(myShowProjectFormatPanel);
  }

  public void apply() throws ConfigurationException {
    String linkedProjectPath = myLinkedProjectPathField.getText();
    if (StringUtil.isEmpty(linkedProjectPath)) {
      throw new ConfigurationException(ExternalSystemBundle.message("error.project.undefined"));
    }
    else if (myCurrentProject != null) {
      ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(myExternalSystemId);
      assert manager != null;
      AbstractExternalSystemSettings<?, ?,?> settings = manager.getSettingsProvider().fun(myCurrentProject);
      if (settings.getLinkedProjectSettings(linkedProjectPath) != null) {
        throw new ConfigurationException(ExternalSystemBundle.message("error.project.already.registered"));
      }
    }

    //noinspection ConstantConditions
    myProjectSettings.setExternalProjectPath(ExternalSystemApiUtil.normalizePath(linkedProjectPath));

    myProjectSettingsControl.validate(myProjectSettings);
    myProjectSettingsControl.apply(myProjectSettings);

    if (mySystemSettingsControl != null) {
      mySystemSettingsControl.validate(mySystemSettings);
      mySystemSettingsControl.apply(mySystemSettings);
    }
  }

  @Nullable
  public ProjectFormatPanel getProjectFormatPanel() {
    return myShowProjectFormatPanel ? myProjectFormatPanel : null;
  }
}
