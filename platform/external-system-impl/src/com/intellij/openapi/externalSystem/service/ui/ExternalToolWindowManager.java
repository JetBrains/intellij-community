// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.ui;

import com.intellij.openapi.application.AppUIExecutor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListenerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl;
import com.intellij.ui.AppUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

/**
 * We want to hide an external system tool window when last external project is unlinked from the current ide project
 * and show it when the first external project is linked to the ide project.
 * <p/>
 * This class encapsulates that functionality.
 */
final class ExternalToolWindowManager implements ExternalSystemSettingsListenerEx {
  @Override
  public void onProjectsLoaded(@NotNull Project project,
                               @NotNull ExternalSystemManager<?, ?, ?, ?, ?> manager,
                               @NotNull Collection<? extends ExternalProjectSettings> settingList) {
  }

  @Override
  public void onProjectsLinked(@NotNull Project project,
                               @NotNull ExternalSystemManager<?, ?, ?, ?, ?> manager,
                               @NotNull Collection<? extends ExternalProjectSettings> __) {
    // https://youtrack.jetbrains.com/issue/IDEA-289729
    if (project.isDefault()) {
      return;
    }

    StartupManager startupManager = StartupManager.getInstance(project);
    // show tool window, but only if linked after start-up
    boolean showToolWindow = startupManager.postStartupActivityPassed();
    startupManager.runAfterOpened(() -> {
      ToolWindow toolWindow = getToolWindow(project, manager.getSystemId());
      AbstractExternalSystemSettings<?, ?, ?> settings = manager.getSettingsProvider().fun(project);
      if (toolWindow != null) {
        activate(toolWindow, settings, showToolWindow);
      }
      else {
        AppUIExecutor.onUiThread(ModalityState.nonModal()).expireWith(settings).later().execute(() -> {
          WriteIntentReadAction.run((Runnable)() -> {
            ToolWindowManager.getInstance(settings.getProject()).invokeLater(() -> {
              ToolWindow toolWindow1 = getToolWindow(settings.getProject(), manager.getSystemId());
              if (toolWindow1 != null) {
                activate(toolWindow1, settings, showToolWindow);
              }
            });
          });
        });
      }
    });
  }

  @Override
  public void onProjectsUnlinked(@NotNull Project project,
                                 @NotNull ExternalSystemManager<?, ?, ?, ?, ?> manager,
                                 @NotNull Set<String> linkedProjectPaths) {
    AbstractExternalSystemSettings<?, ?, ?> settings = manager.getSettingsProvider().fun(project);
    if (!settings.getLinkedProjectsSettings().isEmpty()) {
      return;
    }

    ToolWindow toolWindow = getToolWindow(project, manager.getSystemId());
    if (toolWindow != null) {
      AppUIExecutor
        .onUiThread()
        .expireWith(settings)
        .expireWith(toolWindow.getDisposable())
        .execute(() -> toolWindow.setAvailable(false));
    }
  }

  private static void activate(@NotNull ToolWindow toolWindow,
                               @NotNull AbstractExternalSystemSettings<?, ?, ?> settings,
                               boolean showToolWindow) {
    if (toolWindow.isAvailable() && !showToolWindow) {
      return;
    }

    AppUIUtil.invokeLaterIfProjectAlive(toolWindow.getProject(), () -> {
      boolean shouldShow = showToolWindow &&
                           settings.getLinkedProjectsSettings().size() == 1 &&
                           settings.getProject().getUserData(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT) == null;
      toolWindow.setAvailable(true, shouldShow ? toolWindow::show : null);
    });
  }

  public static @Nullable ToolWindow getToolWindow(@NotNull Project project, @NotNull ProjectSystemId externalSystemId) {
    ToolWindow result = ToolWindowManager.getInstance(project).getToolWindow(externalSystemId.getReadableName());
    if (result == null && ApplicationManager.getApplication().isUnitTestMode()) {
      result = new ToolWindowHeadlessManagerImpl.MockToolWindow(project);
    }
    return result;
  }
}
