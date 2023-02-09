// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project.wizard;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class AbstractExternalModuleBuilder<S extends ExternalProjectSettings> extends ModuleBuilder {
  @NotNull private final Icon myIcon;
  @NotNull private final ProjectSystemId myExternalSystemId;
  @NotNull private final S myExternalProjectSettings;

  protected AbstractExternalModuleBuilder(@NotNull final ProjectSystemId externalSystemId,
                                          @NotNull final S externalProjectSettings) {
    myExternalSystemId = externalSystemId;
    myExternalProjectSettings = externalProjectSettings;
    externalProjectSettings.setupNewProjectDefault();
    Icon icon = ExternalSystemUiUtil.getUiAware(externalSystemId).getProjectIcon();
    myIcon = icon == null ? super.getNodeIcon() : icon;
  }

  @Override
  public @NonNls String getBuilderId() {
    return getClass().getName();
  }

  @Override
  public String getPresentableName() {
    return myExternalSystemId.getReadableName();
  }

  @Override
  @Nls(capitalization = Nls.Capitalization.Sentence)
  public String getDescription() {
    return ExternalSystemBundle.message("module.type.description", myExternalSystemId.getReadableName());
  }

  @Override
  public Icon getNodeIcon() {
    return myIcon;
  }

  @NotNull
  public S getExternalProjectSettings() {
    return myExternalProjectSettings;
  }

  @Nullable
  @Override
  public Project createProject(String name, String path) {
    Project project = super.createProject(name, path);
    if(project != null) {
      project.putUserData(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT, Boolean.TRUE);
    }
    return project;
  }
}
