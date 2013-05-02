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
package com.intellij.openapi.externalSystem.util;

import com.intellij.execution.rmi.RemoteUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemResolveProjectTask;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.service.project.PlatformFacade;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Denis Zhdanov
 * @since 4/22/13 9:36 AM
 */
public class ExternalSystemUtil {

  private ExternalSystemUtil() {
  }

  @Nullable
  public static <T> T getToolWindowElement(@NotNull Class<T> clazz,
                                           @Nullable DataContext context,
                                           @NotNull DataKey<T> key,
                                           @NotNull ProjectSystemId externalSystemId)
  {
    // TODO den use external system
    if (context != null) {
      final T result = key.getData(context);
      if (result != null) {
        return result;
      }
    }

    if (context == null) {
      return null;
    }

    final Project project = PlatformDataKeys.PROJECT.getData(context);
    if (project == null) {
      return null;
    }

    return getToolWindowElement(clazz, project, key, externalSystemId);
  }

  @SuppressWarnings("unchecked")
  @Nullable
  public static <T> T getToolWindowElement(@NotNull Class<T> clazz,
                                           @NotNull Project project,
                                           @NotNull DataKey<T> key,
                                           @NotNull ProjectSystemId externalSystemId)
  {
    final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
    if (toolWindowManager == null) {
      return null;
    }
    // TODO den use external system.
    final ToolWindow toolWindow = null;
//    final ToolWindow toolWindow = toolWindowManager.getToolWindow(GradleConstants.TOOL_WINDOW_ID);
    if (toolWindow == null) {
      return null;
    }

    final ContentManager contentManager = toolWindow.getContentManager();
    if (contentManager == null) {
      return null;
    }

    for (Content content : contentManager.getContents()) {
      final JComponent component = content.getComponent();
      if (component instanceof DataProvider) {
        final Object data = ((DataProvider)component).getData(key.getName());
        if (data != null && clazz.isInstance(data)) {
          return (T)data;
        }
      }
    }
    return null;
  }

  /**
   * {@link RemoteUtil#unwrap(Throwable) unwraps} given exception if possible and builds error message for it.
   *
   * @param e  exception to process
   * @return   error message for the given exception
   */
  @SuppressWarnings({"ThrowableResultOfMethodCallIgnored", "IOResourceOpenedButNotSafelyClosed"})
  @NotNull
  public static String buildErrorMessage(@NotNull Throwable e) {
    Throwable unwrapped = RemoteUtil.unwrap(e);
    String reason = unwrapped.getLocalizedMessage();
    if (!StringUtil.isEmpty(reason)) {
      return reason;
    }
    else if (unwrapped.getClass() == ExternalSystemException.class) {
      return String.format("exception during working with external system: %s", ((ExternalSystemException)unwrapped).getOriginalReason());
    }
    else {
      StringWriter writer = new StringWriter();
      unwrapped.printStackTrace(new PrintWriter(writer));
      return writer.toString();
    }
  }

  /**
   * Asks to refresh all external projects of the target external system linked to the given ide project.
   * <p/>
   * 'Refresh' here means 'obtain the most up-to-date version and apply it to the ide'. 
   * 
   * @param project           target ide project
   * @param externalSystemId  target external system which projects should be refreshed
   */
  public static void refreshProjects(@NotNull final Project project, @NotNull ProjectSystemId externalSystemId) {
    ExternalSystemManager<?,?,?,?,?> manager = ExternalSystemApiUtil.getManager(externalSystemId);
    if (manager == null) {
      return;
    }
    AbstractExternalSystemSettings<?, ?> settings = manager.getSettingsProvider().fun(project);
    Collection<? extends ExternalProjectSettings> projectsSettings = settings.getLinkedProjectsSettings();
    if (projectsSettings.isEmpty()) {
      return;
    }

    final ProjectDataManager projectDataManager = ServiceManager.getService(ProjectDataManager.class);
    final Set<String> externalModuleNames = ContainerUtilRt.newHashSet();
    ExternalProjectRefreshCallback callback = new ExternalProjectRefreshCallback() {
      @Override
      public void onSuccess(@Nullable DataNode<ProjectData> externalProject) {
        if (externalProject == null) {
          return;
        }
        Collection<DataNode<ModuleData>> moduleNodes = ExternalSystemApiUtil.findAll(externalProject, ProjectKeys.MODULE);
        for (DataNode<ModuleData> node : moduleNodes) {
          externalModuleNames.add(node.getData().getName());
        }
        projectDataManager.importData(externalProject.getKey(), Collections.singleton(externalProject), project, false);
      }

      @Override
      public void onFailure(@NotNull String errorMessage, @Nullable String errorDetails) {
      }
    };
    for (ExternalProjectSettings setting : projectsSettings) {
      refreshProject(project, externalSystemId, setting.getExternalProjectPath(), callback, true, false);
    }
    PlatformFacade platformFacade = ServiceManager.getService(PlatformFacade.class);
    List<Module> orphanIdeModules = ContainerUtilRt.newArrayList();
    String externalSystemIdAsString = externalSystemId.toString();
    for (Module module : platformFacade.getModules(project)) {
      String s = module.getOptionValue(ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY);
      if (externalSystemIdAsString.equals(s) && !externalModuleNames.contains(module.getName())) {
        orphanIdeModules.add(module);
      }
    }
    
    // TODO den offer to remove orphan modules here
  }

  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  @Nullable
  private static String extractDetails(@NotNull Throwable e) {
    final Throwable unwrapped = RemoteUtil.unwrap(e);
    if (unwrapped instanceof ExternalSystemException) {
      return ((ExternalSystemException)unwrapped).getOriginalReason();
    }
    return null;
  }

  /**
   * Queries slave gradle process to refresh target gradle project.
   *
   * @param project             target intellij project to use
   * @param externalProjectPath path of the target gradle project's file
   * @param callback            callback to be notified on refresh result
   * @param resolveLibraries    flag that identifies whether gradle libraries should be resolved during the refresh
   * @return the most up-to-date gradle project (if any)
   */
  public static void refreshProject(@NotNull final Project project,
                                    @NotNull final ProjectSystemId externalSystemId,
                                    @NotNull final String externalProjectPath,
                                    @NotNull final ExternalProjectRefreshCallback callback,
                                    final boolean resolveLibraries,
                                    final boolean modal)
  {
    final TaskUnderProgress refreshProjectStructureTask = new TaskUnderProgress() {
      @SuppressWarnings({"ThrowableResultOfMethodCallIgnored", "IOResourceOpenedButNotSafelyClosed"})
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        ExternalSystemResolveProjectTask task
          = new ExternalSystemResolveProjectTask(externalSystemId, project, externalProjectPath, resolveLibraries);
        task.execute(indicator);
        final Throwable error = task.getError();
        if (error == null) {
          DataNode<ProjectData> externalProject = task.getExternalProject();
          callback.onSuccess(externalProject);
          return;
        }
        String message = buildErrorMessage(error);
        if (StringUtil.isEmpty(message)) {
          message = String.format(
            "Can't resolve %s project at '%s'. Reason: %s",
            ExternalSystemApiUtil.toReadableName(externalSystemId), externalProjectPath, message
          );
        }
        callback.onFailure(message, extractDetails(error));
      }
    };

    // TODO den uncomment
    //final TaskUnderProgress refreshTasksTask = new TaskUnderProgress() {
    //  @Override
    //  public void execute(@NotNull ProgressIndicator indicator) {
    //    final ExternalSystemRefreshTasksListTask task = new ExternalSystemRefreshTasksListTask(project, externalProjectPath);
    //    task.execute(indicator);
    //  }
    //};

    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        if (modal) {
          String title = ExternalSystemBundle.message("progress.import.text", ExternalSystemApiUtil.toReadableName(externalSystemId));
          ProgressManager.getInstance().run(new Task.Modal(project, title, false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
              refreshProjectStructureTask.execute(indicator);
              // TODO den uncomment
              //setTitle(ExternalSystemBundle.message("gradle.task.progress.initial.text"));
              //refreshTasksTask.execute(indicator);
            }
          });
        }
        else {
          String title = ExternalSystemBundle.message("progress.refresh.text", ExternalSystemApiUtil.toReadableName(externalSystemId));
          ProgressManager.getInstance().run(new Task.Backgroundable(project, title) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
              refreshProjectStructureTask.execute(indicator);
              // TODO den uncomment
              //setTitle(ExternalSystemBundle.message("gradle.task.progress.initial.text"));
              //refreshTasksTask.execute(indicator);
            }
          });
        }
      }
    });
  }

  private interface TaskUnderProgress {
    void execute(@NotNull ProgressIndicator indicator);
  }
}
