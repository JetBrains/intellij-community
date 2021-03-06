// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.newProjectWizard;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
@State(
  name = "SelectProjectTemplateSettings",
  storages = @Storage(value = "projectSelectTemplate.xml", roamingType = RoamingType.DISABLED)
)
public class SelectTemplateSettings implements PersistentStateComponent<SelectTemplateSettings> {
  public boolean EXPERT_MODE = false;
  public String LAST_TEMPLATE = null;

  public static SelectTemplateSettings getInstance() {
    return ApplicationManager.getApplication().getService(SelectTemplateSettings.class);
  }

  @Nullable
  public String getLastGroup() {
    return LAST_TEMPLATE == null ? null : LAST_TEMPLATE.split("/")[0];
  }

  @Nullable
  public String getLastTemplate() {
    if (LAST_TEMPLATE == null) {
      return null;
    }
    else {
      String[] split = LAST_TEMPLATE.split("/");
      return split.length > 1 ? split[1] : null;
    }
  }

  public void setLastTemplate(String group, String template) {
    LAST_TEMPLATE = group + "/" + template;
  }

  @NotNull
  @Override
  public SelectTemplateSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull SelectTemplateSettings state) {
    EXPERT_MODE = state.EXPERT_MODE;
    LAST_TEMPLATE = state.LAST_TEMPLATE;
  }
}
