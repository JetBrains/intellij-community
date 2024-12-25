// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.settings;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemSettingsControl;
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil;
import com.intellij.openapi.externalSystem.util.PaintAwarePanel;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Templates class for managing single external project settings (single ide project might contain multiple bindings to external
 * projects, e.g. one module is backed by a single external project and couple of others are backed by a single external multi-project).
 */
public abstract class AbstractExternalProjectSettingsControl<S extends ExternalProjectSettings>
  extends AbstractSettingsControl
  implements ExternalSystemSettingsControl<S> {

  private final @NotNull S myInitialSettings;

  protected AbstractExternalProjectSettingsControl(@NotNull S initialSettings) {
    this(null, initialSettings);
  }

  protected AbstractExternalProjectSettingsControl(@Nullable Project project, @NotNull S initialSettings) {
    super(project);
    myInitialSettings = initialSettings;
  }

  public @NotNull S getInitialSettings() {
    return myInitialSettings;
  }

  @Override
  public void fillUi(@NotNull PaintAwarePanel canvas, int indentLevel) {
    fillExtraControls(canvas, indentLevel);
  }
  
  protected abstract void fillExtraControls(@NotNull PaintAwarePanel content, int indentLevel);

  @Override
  public boolean isModified() {
    return isExtraSettingModified();
  }

  protected abstract boolean isExtraSettingModified();

  @Override
  public void reset() {
    reset(false, null);
  }

  @Override
  public void reset(@Nullable Project project) {
    reset(false, project);
  }

  @Override
  public void reset(@Nullable WizardContext wizardContext) {
    reset(false, wizardContext, null);
  }

  public void reset(boolean isDefaultModuleCreation, @Nullable Project project) {
    reset(isDefaultModuleCreation, null, project);
  }

  public void reset(boolean isDefaultModuleCreation, @Nullable WizardContext wizardContext, @Nullable Project project) {
    super.reset(wizardContext, project);
    resetExtraSettings(isDefaultModuleCreation, wizardContext);
  }

  protected abstract void resetExtraSettings(boolean isDefaultModuleCreation);

  protected void resetExtraSettings(boolean isDefaultModuleCreation, @Nullable WizardContext wizardContext) {
    resetExtraSettings(isDefaultModuleCreation);
  }

  @Override
  public void apply(@NotNull S settings) {
    settings.setModules(myInitialSettings.getModules());
    if (myInitialSettings.getExternalProjectPath() != null) {
      settings.setExternalProjectPath(myInitialSettings.getExternalProjectPath());
    }
    applyExtraSettings(settings);
  }

  protected abstract void applyExtraSettings(@NotNull S settings);

  @Override
  public void disposeUIResources() {
    ExternalSystemUiUtil.disposeUi(this);
  }
  
  @Override
  public void showUi(boolean show) {
    ExternalSystemUiUtil.showUi(this, show);
  }

  public void updateInitialSettings() {
    updateInitialExtraSettings();
  }

  protected void updateInitialExtraSettings(){}

  /**
   * see {@linkplain AbstractImportFromExternalSystemControl#setCurrentProject(Project)}
   */
  public void setCurrentProject(@Nullable Project project) {
    setProject(project);
  }

  @Override
  public @Nullable Project getProject() {
    return super.getProject();
  }
}
