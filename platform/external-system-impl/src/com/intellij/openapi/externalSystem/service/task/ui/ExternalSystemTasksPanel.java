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
package com.intellij.openapi.externalSystem.service.task.ui;

import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Zhdanov
 * @since 5/12/13 10:18 PM
 */
public class ExternalSystemTasksPanel extends SimpleToolWindowPanel implements DataProvider {

  @NotNull private final ExternalSystemTasksTreeModel myAllTasksModel;
  @NotNull private final ExternalSystemTasksTree      myAllTasksTree;
  @NotNull private final ProjectSystemId              myExternalSystemId;
  @NotNull private final NotificationGroup            myNotificationGroup;

  public ExternalSystemTasksPanel(@NotNull Project project,
                                  @NotNull ProjectSystemId externalSystemId,
                                  @NotNull NotificationGroup notificationGroup)
  {
    super(true);
    myExternalSystemId = externalSystemId;
    myNotificationGroup = notificationGroup;
    myAllTasksModel = new ExternalSystemTasksTreeModel(externalSystemId);

    ExternalSystemManager<?,?,?,?,?> manager = ExternalSystemApiUtil.getManager(externalSystemId);
    assert manager != null;
    AbstractExternalSystemLocalSettings settings = manager.getLocalSettingsProvider().fun(project);
    myAllTasksTree = new ExternalSystemTasksTree(myAllTasksModel, settings.getExpandStates());

    ActionManager actionManager = ActionManager.getInstance();
    ActionGroup group = (ActionGroup)actionManager.getAction(ExternalSystemConstants.TOOL_WINDOW_TOOLBAR_ACTIONS_GROUP_ID);
    ActionToolbar toolbar = actionManager.createActionToolbar(ExternalSystemConstants.TOOL_WINDOW_PLACE, group, true);
    toolbar.setTargetComponent(this);
    setToolbar(toolbar.getComponent());

    setContent(new JBScrollPane(myAllTasksTree));

    ExternalSystemUiUtil.apply(settings, myAllTasksModel);
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (ExternalSystemDataKeys.ALL_TASKS_MODEL.is(dataId)) {
      return myAllTasksModel;
    }
    else if (ExternalSystemDataKeys.EXTERNAL_SYSTEM_ID.is(dataId)) {
      return myExternalSystemId;
    }
    else if (ExternalSystemDataKeys.NOTIFICATION_GROUP.is(dataId)) {
      return myNotificationGroup;
    }
    return null;
  }
}
