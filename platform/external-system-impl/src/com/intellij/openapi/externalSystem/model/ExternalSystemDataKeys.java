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
package com.intellij.openapi.externalSystem.model;

import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskPojo;
import com.intellij.openapi.externalSystem.model.project.ExternalProjectPojo;
import com.intellij.openapi.externalSystem.service.task.ui.ExternalSystemRecentTasksList;
import com.intellij.openapi.externalSystem.service.task.ui.ExternalSystemTasksTreeModel;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 2/7/12 11:19 AM
 */
public class ExternalSystemDataKeys {

  @NotNull public static final DataKey<ProjectSystemId>              EXTERNAL_SYSTEM_ID = DataKey.create("external.system.id");
  @NotNull public static final DataKey<NotificationGroup>            NOTIFICATION_GROUP = DataKey.create("external.system.notification");
  @NotNull public static final DataKey<ExternalSystemTasksTreeModel> ALL_TASKS_MODEL    = DataKey.create("external.system.all.tasks.model");
  @NotNull public static final DataKey<ExternalTaskPojo>             SELECTED_TASK      = DataKey.create("external.system.selected.task");
  @NotNull public static final DataKey<ExternalProjectPojo>          SELECTED_PROJECT   = DataKey.create("external.system.selected.project");

  @NotNull public static final DataKey<ExternalSystemRecentTasksList> RECENT_TASKS_LIST
    = DataKey.create("external.system.recent.tasks.list");

  @NotNull public static final Key<Boolean> NEWLY_IMPORTED_PROJECT = new Key<Boolean>("external.system.newly.imported");

  private ExternalSystemDataKeys() {
  }
}
