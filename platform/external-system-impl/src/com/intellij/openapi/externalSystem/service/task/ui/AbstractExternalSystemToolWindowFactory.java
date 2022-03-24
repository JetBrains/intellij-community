// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.task.ui;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManager;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
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
 */
public abstract class AbstractExternalSystemToolWindowFactory implements ToolWindowFactory, DumbAware {
  @NotNull private final ProjectSystemId externalSystemId;

  protected AbstractExternalSystemToolWindowFactory(@NotNull ProjectSystemId id) {
    externalSystemId = id;
  }

  protected abstract @NotNull AbstractExternalSystemSettings<?, ?, ?> getSettings(@NotNull Project project);

  @Override
  public boolean shouldBeAvailable(@NotNull Project project) {
    return !getSettings(project).getLinkedProjectsSettings().isEmpty();
  }

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    toolWindow.setTitle(externalSystemId.getReadableName());
    ContentManager contentManager = toolWindow.getContentManager();
    contentManager.addContent(new ContentImpl(createInitializingLabel(), "", false));

    ExternalProjectsManager.getInstance(project).runWhenInitialized(() -> {
      ExternalProjectsViewImpl projectView = new ExternalProjectsViewImpl(project, (ToolWindowEx)toolWindow, externalSystemId);
      ExternalProjectsManagerImpl.getInstance(project).registerView(projectView);
      ContentImpl taskContent = new ContentImpl(projectView, "", true);
      contentManager.removeAllContents(true);
      contentManager.addContent(taskContent);
    });
  }

  private @NotNull JLabel createInitializingLabel() {
    JLabel label =
      new JLabel(ExternalSystemBundle.message("initializing.0.projects.data", externalSystemId.getReadableName()), SwingConstants.CENTER);
    label.setOpaque(true);
    return label;
  }
}
