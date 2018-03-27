/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManager;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.view.ExternalProjectsViewImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.impl.ContentImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Denis Zhdanov
 * @since 5/13/13 4:15 PM
 */
public abstract class AbstractExternalSystemToolWindowFactory implements ToolWindowFactory, DumbAware {

  @NotNull private final ProjectSystemId myExternalSystemId;

  protected AbstractExternalSystemToolWindowFactory(@NotNull ProjectSystemId id) {
    myExternalSystemId = id;
  }

  @Override
  public void createToolWindowContent(@NotNull final Project project, @NotNull final ToolWindow toolWindow) {
    toolWindow.setTitle(myExternalSystemId.getReadableName());
    ContentManager contentManager = toolWindow.getContentManager();

    contentManager.addContent(new ContentImpl(createInitializingLabel(), "", false));

    ExternalProjectsManager.getInstance(project).runWhenInitialized(
      () -> ApplicationManager.getApplication().invokeLater(() -> {
        final ExternalProjectsViewImpl projectsView = new ExternalProjectsViewImpl(project, (ToolWindowEx)toolWindow, myExternalSystemId);
        ExternalProjectsManagerImpl.getInstance(project).registerView(projectsView);
        ContentImpl tasksContent = new ContentImpl(projectsView, ExternalSystemBundle.message("tool.window.title.projects"), true);
        contentManager.removeAllContents(true);
        contentManager.addContent(tasksContent);
      }));
  }

  @NotNull
  private JLabel createInitializingLabel() {
    JLabel label = new JLabel("Initializing " + myExternalSystemId.getReadableName() + " projects data...", SwingConstants.CENTER);
    label.setOpaque(true);
    return label;
  }
}
