// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.templates;

import com.intellij.lang.LangBundle;
import com.intellij.openapi.components.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@State(name = "ProjectTemplates",
  storages = @Storage(value = "ProjectTemplates.xml", exportable = true, roamingType = RoamingType.DISABLED),//no non-default state, so won't ever be created
  additionalExportDirectory = "projectTemplates",
  presentableName = ProjectTemplateExportable.NameGetter.class,
  category = SettingsCategory.CODE)
public final class ProjectTemplateExportable implements PersistentStateComponent<ProjectTemplateExportable.ProjectTemplateExportableState> {
  @Override
  public @Nullable ProjectTemplateExportable.ProjectTemplateExportableState getState() {
    return null;
  }

  @Override
  public void loadState(@NotNull ProjectTemplateExportableState state) {
    //ignore
  }

  public static class ProjectTemplateExportableState {
  }

  public static class NameGetter extends State.NameGetter {

    @Override
    public @Nls String get() {
      return LangBundle.message("project.template.presentable.name");
    }
  }
}
