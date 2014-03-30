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
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.impl.ContentImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 5/13/13 4:15 PM
 */
public abstract class AbstractExternalSystemToolWindowFactory implements ToolWindowFactory, DumbAware {

  @NotNull private final ProjectSystemId   myExternalSystemId;
  @NotNull private final NotificationGroup myNotificationGroup;

  protected AbstractExternalSystemToolWindowFactory(@NotNull ProjectSystemId id) {
    myExternalSystemId = id;
    myNotificationGroup = NotificationGroup.toolWindowGroup("notification.group.id." + id.toString().toLowerCase(),
                                                            myExternalSystemId.getReadableName(),
                                                            true);
  }

  @Override
  public void createToolWindowContent(final Project project, final ToolWindow toolWindow) {
    toolWindow.setTitle(myExternalSystemId.getReadableName());
    ContentManager contentManager = toolWindow.getContentManager();
    String tasksTitle = ExternalSystemBundle.message("tool.window.title.tasks");
    ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(myExternalSystemId);
    assert manager != null;
    ExternalSystemTasksPanel panel = new ExternalSystemTasksPanel(project, myExternalSystemId, myNotificationGroup);
    ContentImpl tasksContent = new ContentImpl(panel, tasksTitle, true);
    contentManager.addContent(tasksContent);
  }
}
