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
package com.intellij.openapi.externalSystem.action;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ExternalProjectPojo;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 * @since 9/18/13
 */
public class ExternalActionUtil {
  @NotNull
  public static MyInfo getProcessingInfo(@NotNull DataContext context) {
    ExternalProjectPojo externalProject = ExternalSystemDataKeys.SELECTED_PROJECT.getData(context);
    if (externalProject == null) {
      return MyInfo.EMPTY;
    }

    ProjectSystemId externalSystemId = ExternalSystemDataKeys.EXTERNAL_SYSTEM_ID.getData(context);
    if (externalSystemId == null) {
      return MyInfo.EMPTY;
    }

    Project ideProject = CommonDataKeys.PROJECT.getData(context);
    if (ideProject == null) {
      return MyInfo.EMPTY;
    }

    AbstractExternalSystemSettings<?, ?, ?> settings = ExternalSystemApiUtil.getSettings(ideProject, externalSystemId);
    ExternalProjectSettings externalProjectSettings = settings.getLinkedProjectSettings(externalProject.getPath());
    AbstractExternalSystemLocalSettings localSettings = ExternalSystemApiUtil.getLocalSettings(ideProject, externalSystemId);

    return new MyInfo(externalProjectSettings == null ? null : settings,
                      localSettings == null ? null : localSettings,
                      externalProjectSettings == null ? null : externalProject,
                      ideProject,
                      externalSystemId);
  }

  public static class MyInfo {

    public static final MyInfo EMPTY = new MyInfo(null, null, null, null, null);

    @Nullable public final AbstractExternalSystemSettings<?, ?, ?> settings;
    @Nullable public final AbstractExternalSystemLocalSettings  localSettings;
    @Nullable public final ExternalProjectPojo                  externalProject;
    @Nullable public final Project                              ideProject;
    @Nullable public final ProjectSystemId                      externalSystemId;

    MyInfo(@Nullable AbstractExternalSystemSettings<?, ?, ?> settings,
           @Nullable AbstractExternalSystemLocalSettings localSettings,
           @Nullable ExternalProjectPojo externalProject,
           @Nullable Project ideProject,
           @Nullable ProjectSystemId externalSystemId)
    {
      this.settings = settings;
      this.localSettings = localSettings;
      this.externalProject = externalProject;
      this.ideProject = ideProject;
      this.externalSystemId = externalSystemId;
    }
  }
}

