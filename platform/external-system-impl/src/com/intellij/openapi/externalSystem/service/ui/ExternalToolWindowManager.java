package com.intellij.openapi.externalSystem.service.ui;

import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListenerAdapter;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

/**
 * We want to hide an external system tool window when last external project is unlinked from the current ide project
 * and show it when the first external project is linked to the ide project.
 * <p/>
 * This class encapsulates that functionality.
 * 
 * @author Denis Zhdanov
 * @since 6/14/13 7:01 PM
 */
public class ExternalToolWindowManager {

  @SuppressWarnings("unchecked")
  public static void handle(@NotNull final Project project) {
    for (final ExternalSystemManager<?, ?, ?, ?, ?> manager : ExternalSystemApiUtil.getAllManagers()) {
      final AbstractExternalSystemSettings settings = manager.getSettingsProvider().fun(project);
      settings.subscribe(new ExternalSystemSettingsListenerAdapter() {
        @Override
        public void onProjectsLinked(@NotNull Collection linked) {
          if (settings.getLinkedProjectsSettings().size() != 1) {
            return;
          }
          ToolWindow toolWindow = getToolWindow(project, manager.getSystemId());
          if (toolWindow != null) {
            toolWindow.setAvailable(true, null);
          }
          else {
            StartupManager.getInstance(project).runWhenProjectIsInitialized(new DumbAwareRunnable() {
              @Override
              public void run() {
                if (project.isDisposed()) return;

                ExternalSystemUtil.ensureToolWindowInitialized(project, manager.getSystemId());
                ToolWindowManager.getInstance(project).invokeLater(new Runnable() {
                  public void run() {
                    if (project.isDisposed()) return;
                    ToolWindow toolWindow = getToolWindow(project, manager.getSystemId());
                    if (toolWindow != null) {
                      toolWindow.setAvailable(true, null);
                    }
                  }
                });
              }
            });
          }
        }

        @Override
        public void onProjectsUnlinked(@NotNull Set linkedProjectPaths) {
          if (!settings.getLinkedProjectsSettings().isEmpty()) {
            return;
          }
          final ToolWindow toolWindow = getToolWindow(project, manager.getSystemId());
          if (toolWindow != null) {
            UIUtil.invokeLaterIfNeeded(new Runnable() {
              @Override
              public void run() {
                toolWindow.setAvailable(false, null);
              }
            });
          }
        }
      });
    }
  }

  @Nullable
  private static ToolWindow getToolWindow(@NotNull Project project, @NotNull ProjectSystemId externalSystemId) {
    final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
    if (toolWindowManager == null) {
      return null;
    }
    ToolWindow result = toolWindowManager.getToolWindow(externalSystemId.getReadableName());
    if (result instanceof ToolWindowImpl) {
      ((ToolWindowImpl)result).ensureContentInitialized();
    }
    return result;
  }
}
