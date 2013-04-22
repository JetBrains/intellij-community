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
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.project.ProjectEntityData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemResolveProjectTask;
import com.intellij.openapi.externalSystem.service.project.ModuleAwareContentRoot;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.Consumer;
import com.intellij.util.PathsList;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * @author Denis Zhdanov
 * @since 4/22/13 9:36 AM
 */
public class ExternalSystemUtil {

  private static final Logger LOG = Logger.getInstance("#" + ExternalSystemUtil.class.getName());

  private ExternalSystemUtil() {
  }

  /**
   * Tries to dispatch given entity via the given visitor.
   *
   * @param entity   intellij project entity candidate to dispatch
   * @param visitor  dispatch callback to use for the given entity
   */
  public static void dispatch(@Nullable Object entity, @NotNull IdeEntityVisitor visitor) {
    if (entity instanceof Project) {
      visitor.visit(((Project)entity));
    }
    else if (entity instanceof Module) {
      visitor.visit(((Module)entity));
    }
    else if (entity instanceof ModuleAwareContentRoot) {
      visitor.visit(((ModuleAwareContentRoot)entity));
    }
    else if (entity instanceof LibraryOrderEntry) {
      visitor.visit(((LibraryOrderEntry)entity));
    }
    else if (entity instanceof ModuleOrderEntry) {
      visitor.visit(((ModuleOrderEntry)entity));
    }
    else if (entity instanceof Library) {
      visitor.visit(((Library)entity));
    }
  }

  @NotNull
  public static ProjectSystemId detectOwner(@Nullable ProjectEntityData externalEntity, @Nullable Object ideEntity) {
    if (ideEntity != null) {
      return ProjectSystemId.IDE;
    }
    else if (externalEntity != null) {
      return externalEntity.getOwner();
    }
    else {
      throw new RuntimeException(String.format(
        "Can't detect owner system for the given arguments: external=%s, ide=%s", externalEntity, ideEntity
      ));
    }
  }

  @NotNull
  public static String getOutdatedEntityName(@NotNull String entityName, @NotNull String gradleVersion, @NotNull String ideVersion) {
    return String.format("%s (%s -> %s)", entityName, ideVersion, gradleVersion);
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

  public static void refreshProject(@NotNull Project project, @NotNull ProjectSystemId externalSystemId) {
    refreshProject(project, externalSystemId, new Ref<String>());
  }

  public static void refreshProject(@NotNull Project project,
                                    @NotNull ProjectSystemId externalSystemId,
                                    @NotNull final Consumer<String> errorCallback)
  {
    final Ref<String> errorMessageHolder = new Ref<String>() {
      @Override
      public void set(@Nullable String value) {
        if (value != null) {
          errorCallback.consume(value);
        }
      }
    };
    refreshProject(project, externalSystemId, errorMessageHolder);
  }

  public static void refreshProject(@NotNull Project project,
                                    @NotNull ProjectSystemId externalSystemId,
                                    @NotNull final Ref<String> errorMessageHolder)
  {
    ExternalSystemSettingsManager settingsManager = ServiceManager.getService(ExternalSystemSettingsManager.class);
    AbstractExternalSystemSettings settings = settingsManager.getSettings(project, externalSystemId);
    final String linkedProjectPath = settings.getLinkedExternalProjectPath();
    if (StringUtil.isEmpty(linkedProjectPath)) {
      return;
    }
    assert linkedProjectPath != null;
    Ref<String> errorDetailsHolder = new Ref<String>() {
      @Override
      public void set(@Nullable String error) {
        if (!StringUtil.isEmpty(error)) {
          assert error != null;
          LOG.warn(error);
        }
      }
    };
    refreshProject(project, externalSystemId, linkedProjectPath, errorMessageHolder, errorDetailsHolder, true, false);
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
   * @param errorMessageHolder  holder for the error message that describes a problem occurred during the refresh (if any)
   * @param errorDetailsHolder  holder for the error details of the problem occurred during the refresh (if any)
   * @param resolveLibraries    flag that identifies whether gradle libraries should be resolved during the refresh
   * @return the most up-to-date gradle project (if any)
   */
  @Nullable
  public static DataNode<ProjectData> refreshProject(@NotNull final Project project,
                                                     @NotNull final ProjectSystemId externalSystemId,
                                                     @NotNull final String externalProjectPath,
                                                     @NotNull final Ref<String> errorMessageHolder,
                                                     @NotNull final Ref<String> errorDetailsHolder,
                                                     final boolean resolveLibraries,
                                                     final boolean modal)
  {
    final Ref<DataNode<ProjectData>> externalProject = new Ref<DataNode<ProjectData>>();
    final TaskUnderProgress refreshProjectStructureTask = new TaskUnderProgress() {
      @SuppressWarnings({"ThrowableResultOfMethodCallIgnored", "IOResourceOpenedButNotSafelyClosed"})
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        ExternalSystemResolveProjectTask task
          = new ExternalSystemResolveProjectTask(externalSystemId, project, externalProjectPath, resolveLibraries);
        task.execute(indicator);
        externalProject.set(task.getExternalProject());
        final Throwable error = task.getError();
        if (error == null) {
          return;
        }
        final String message = buildErrorMessage(error);
        if (StringUtil.isEmpty(message)) {
          errorMessageHolder.set(String.format(
            "Can't resolve %s project at '%s'. Reason: %s",
            ExternalSystemApiUtil.toReadableName(externalSystemId), externalProjectPath, message
          ));
        }
        else {
          errorMessageHolder.set(message);
        }
        errorDetailsHolder.set(extractDetails(error));
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
    return externalProject.get();
  }

  private interface TaskUnderProgress {
    void execute(@NotNull ProgressIndicator indicator);
  }

  
}
