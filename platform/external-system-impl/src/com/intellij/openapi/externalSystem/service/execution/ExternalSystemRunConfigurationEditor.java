package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.util.PaintAwarePanel;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Denis Zhdanov
 * @since 23.05.13 18:34
 */
public class ExternalSystemRunConfigurationEditor extends SettingsEditor<ExternalSystemRunConfiguration> {

  @NotNull private final ExternalSystemTaskSettingsControl myControl;

  public ExternalSystemRunConfigurationEditor(@NotNull Project project, @NotNull ProjectSystemId externalSystemId) {
    myControl = new ExternalSystemTaskSettingsControl(project, externalSystemId);
  }

  @Override
  protected void resetEditorFrom(ExternalSystemRunConfiguration s) {
    myControl.setOriginalSettings(s.getSettings());
    myControl.reset();
  }

  @Override
  protected void applyEditorTo(ExternalSystemRunConfiguration s) throws ConfigurationException {
    myControl.apply(s.getSettings());
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    PaintAwarePanel result = new PaintAwarePanel(new GridBagLayout());
    myControl.fillUi(result, 0);
    return result;
  }

  @Override
  protected void disposeEditor() {
    myControl.disposeUIResources();
  }
}
