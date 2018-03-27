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
package com.intellij.ide.util.newProjectWizard;

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
    return ServiceManager.getService(SelectTemplateSettings.class);
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
  public void loadState(SelectTemplateSettings state) {
    EXPERT_MODE = state.EXPERT_MODE;
    LAST_TEMPLATE = state.LAST_TEMPLATE;
  }
}
