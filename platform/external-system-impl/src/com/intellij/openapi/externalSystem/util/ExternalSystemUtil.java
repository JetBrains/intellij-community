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

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.rmi.RemoteUtil;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.*;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskExecutionInfo;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.execution.AbstractExternalSystemTaskConfigurationType;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemResolveProjectTask;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemIdeNotificationManager;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.service.project.PlatformFacade;
import com.intellij.openapi.externalSystem.service.project.manage.ModuleDataService;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.externalSystem.service.task.ui.ExternalSystemRecentTasksList;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 4/22/13 9:36 AM
 */
public class ExternalSystemUtil {

  private static final Logger LOG = Logger.getInstance("#" + ExternalSystemUtil.class.getName());
  
  @NotNull private static final Map<String, String> RUNNER_IDS = ContainerUtilRt.newHashMap();
  static {
    RUNNER_IDS.put(ToolWindowId.RUN, ExternalSystemConstants.RUNNER_ID);
    RUNNER_IDS.put(ToolWindowId.DEBUG, ExternalSystemConstants.DEBUG_RUNNER_ID);
  }

  private ExternalSystemUtil() {
  }

  @SuppressWarnings("unchecked")
  @Nullable
  public static <T> T getToolWindowElement(@NotNull Class<T> clazz,
                                           @NotNull Project project,
                                           @NotNull DataKey<T> key,
                                           @NotNull ProjectSystemId externalSystemId)
  {
    if (project.isDisposed() || !project.isOpen()) {
      return null;
    }
    final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
    if (toolWindowManager == null) {
      return null;
    }
    final ToolWindow toolWindow = toolWindowManager.getToolWindow(externalSystemId.getReadableName());
    if (toolWindow == null) {
      return null;
    }
    if (toolWindow instanceof ToolWindowImpl) {
      ((ToolWindowImpl)toolWindow).ensureContentInitialized();
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
   * @return error message for the given exception
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
   * @param force             flag which defines if external project refresh should be performed if it's config is up-to-date
   */
  public static void refreshProjects(@NotNull final Project project, @NotNull final ProjectSystemId externalSystemId, boolean force) {
    ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(externalSystemId);
    if (manager == null) {
      return;
    }
    AbstractExternalSystemSettings<?, ?> settings = manager.getSettingsProvider().fun(project);
    final Collection<? extends ExternalProjectSettings> projectsSettings = settings.getLinkedProjectsSettings();
    if (projectsSettings.isEmpty()) {
      return;
    }

    final ProjectDataManager projectDataManager = ServiceManager.getService(ProjectDataManager.class);
    final int[] counter = new int[1]; 

    ExternalProjectRefreshCallback callback = new ExternalProjectRefreshCallback() {

      @NotNull
      private final Set<String> myExternalModuleNames = ContainerUtilRt.newHashSet();

      @Override
      public void onSuccess(@Nullable DataNode<ProjectData> externalProject) {
        if (externalProject == null) {
          return;
        }
        Collection<DataNode<ModuleData>> moduleNodes = ExternalSystemApiUtil.findAll(externalProject, ProjectKeys.MODULE);
        for (DataNode<ModuleData> node : moduleNodes) {
          myExternalModuleNames.add(node.getData().getName());
        }
        projectDataManager.importData(externalProject.getKey(), Collections.singleton(externalProject), project, false);
        if (--counter[0] <= 0) {
          processOrphanModules();
        }
      }

      @Override
      public void onFailure(@NotNull String errorMessage, @Nullable String errorDetails) {
        counter[0] = Integer.MAX_VALUE; // Don't process orphan modules if there was an error on refresh.
      }

      private void processOrphanModules() {
        PlatformFacade platformFacade = ServiceManager.getService(PlatformFacade.class);
        List<Module> orphanIdeModules = ContainerUtilRt.newArrayList();
        String externalSystemIdAsString = externalSystemId.toString();

        for (Module module : platformFacade.getModules(project)) {
          String s = module.getOptionValue(ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY);
          if (externalSystemIdAsString.equals(s) && !myExternalModuleNames.contains(module.getName())) {
            orphanIdeModules.add(module);
          }
        }

        if (!orphanIdeModules.isEmpty()) {
          ruleOrphanModules(orphanIdeModules, project, externalSystemId);
        }
      }
    };

    Map<String, Long> modificationStamps = manager.getLocalSettingsProvider().fun(project).getExternalConfigModificationStamps();
    Set<String> toRefresh = ContainerUtilRt.newHashSet();
    for (ExternalProjectSettings setting : projectsSettings) {
      Long oldModificationStamp = modificationStamps.get(setting.getExternalProjectPath());
      long currentModificationStamp = getTimeStamp(setting.getExternalProjectPath());
      if (force || currentModificationStamp < 0 || oldModificationStamp == null || oldModificationStamp < currentModificationStamp) {
        toRefresh.add(setting.getExternalProjectPath());
      }
    }

    if (!toRefresh.isEmpty()) {
      counter[0] = toRefresh.size();
      for (String path : toRefresh) {
        refreshProject(project, externalSystemId, path, callback, true, false);
      }
    }
  }

  private static long getTimeStamp(@NotNull String path) {
    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(path));
    return vFile == null ? -1 : vFile.getTimeStamp();
  }

  /**
   * There is a possible case that an external module has been un-linked from ide project. There are two ways to process
   * ide modules which correspond to that external project:
   * <pre>
   * <ol>
   *   <li>Remove them from ide project as well;</li>
   *   <li>Keep them at ide project as well;</li>
   * </ol>
   * </pre>
   * This method handles that situation, i.e. it asks a user what should be done and acts accordingly.
   *
   * @param orphanModules     modules which correspond to the un-linked external project
   * @param project           current ide project
   * @param externalSystemId  id of the external system which project has been un-linked from ide project
   */
  public static void ruleOrphanModules(@NotNull final List<Module> orphanModules,
                                       @NotNull final Project project,
                                       @NotNull final ProjectSystemId externalSystemId)
  {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {

        final JPanel content = new JPanel(new GridBagLayout());
        content.add(new JLabel(ExternalSystemBundle.message("orphan.modules.text", externalSystemId.getReadableName())),
                    ExternalSystemUiUtil.getFillLineConstraints(0));

        final CheckBoxList<Module> orphanModulesList = new CheckBoxList<Module>();
        orphanModulesList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        orphanModulesList.setItems(orphanModules, new Function<Module, String>() {
          @Override
          public String fun(Module module) {
            return module.getName();
          }
        });
        for (Module module : orphanModules) {
          orphanModulesList.setItemSelected(module, true);
        }
        orphanModulesList.setBorder(IdeBorderFactory.createEmptyBorder(8));
        content.add(orphanModulesList, ExternalSystemUiUtil.getFillLineConstraints(0));
        content.setBorder(IdeBorderFactory.createEmptyBorder(0, 0, 8, 0));

        DialogWrapper dialog = new DialogWrapper(project) {

          {
            setTitle(ExternalSystemBundle.message("import.title", externalSystemId.getReadableName()));
            init();
          }

          @Nullable
          @Override
          protected JComponent createCenterPanel() {
            return new JBScrollPane(content);
          }
        };
        boolean ok = dialog.showAndGet();
        if (!ok) {
          return;
        }

        List<Module> toRemove = ContainerUtilRt.newArrayList();
        for (int i = 0; i < orphanModules.size(); i++) {
          Module module = orphanModules.get(i);
          if (orphanModulesList.isItemSelected(i)) {
            toRemove.add(module);
          }
          else {
            ModuleDataService.unlinkModuleFromExternalSystem(module);
          }
        }

        if (!toRemove.isEmpty()) {
          ServiceManager.getService(ProjectDataManager.class).removeData(ProjectKeys.MODULE, toRemove, project, true);
        }
      }
    });
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
    final String projectName = new File(externalProjectPath).getParentFile().getName();
    final TaskUnderProgress refreshProjectStructureTask = new TaskUnderProgress() {
      @SuppressWarnings({"ThrowableResultOfMethodCallIgnored", "IOResourceOpenedButNotSafelyClosed"})
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        ExternalSystemResolveProjectTask task
          = new ExternalSystemResolveProjectTask(externalSystemId, project, externalProjectPath, resolveLibraries);
        task.execute(indicator);
        final Throwable error = task.getError();
        if (error == null) {
          long stamp = getTimeStamp(externalProjectPath);
          if (stamp > 0) {
            ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(externalSystemId);
            assert manager != null;
            manager.getLocalSettingsProvider().fun(project).getExternalConfigModificationStamps().put(externalProjectPath, stamp);
          }
          DataNode<ProjectData> externalProject = task.getExternalProject();
          callback.onSuccess(externalProject);
          return;
        }
        String message = buildErrorMessage(error);
        if (StringUtil.isEmpty(message)) {
          message = String.format(
            "Can't resolve %s project at '%s'. Reason: %s",
            externalSystemId.getReadableName(), externalProjectPath, message
          );
        }

        callback.onFailure(message, extractDetails(error));

        ExternalSystemIdeNotificationManager notificationManager = ServiceManager.getService(ExternalSystemIdeNotificationManager.class);
        if (notificationManager != null) {
          notificationManager.processExternalProjectRefreshError(message, project, projectName, externalSystemId);
        }
      }
    };

    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        if (modal) {
          String title = ExternalSystemBundle.message("progress.import.text", projectName, externalSystemId.getReadableName());
          ProgressManager.getInstance().run(new Task.Modal(project, title, false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
              refreshProjectStructureTask.execute(indicator);
            }
          });
        }
        else {
          String title = ExternalSystemBundle.message("progress.refresh.text", projectName, externalSystemId.getReadableName());
          ProgressManager.getInstance().run(new Task.Backgroundable(project, title) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
              refreshProjectStructureTask.execute(indicator);
            }
          });
        }
      }
    });
  }

  public static void runTask(@NotNull ExternalSystemTaskExecutionSettings taskSettings,
                             @NotNull String executorId,
                             @NotNull Project project,
                             @NotNull ProjectSystemId externalSystemId)
  {
    Executor executor = ExecutorRegistry.getInstance().getExecutorById(executorId);
    if (executor == null) {
      return;
    }
    String runnerId = getRunnerId(executorId);
    if (runnerId == null) {
      return;
    }
    ProgramRunner runner = RunnerRegistry.getInstance().findRunnerById(runnerId);
    if (runner == null) {
      return;
    }
    AbstractExternalSystemTaskConfigurationType configurationType = findConfigurationType(externalSystemId);
    if (configurationType == null) {
      return;
    }

    String name = AbstractExternalSystemTaskConfigurationType.generateName(project, taskSettings);
    RunnerAndConfigurationSettings settings = RunManagerEx.getInstanceEx(project).createConfiguration(name, configurationType.getFactory());
    ExternalSystemRunConfiguration runConfiguration = (ExternalSystemRunConfiguration)settings.getConfiguration();
    runConfiguration.getSettings().setExternalProjectPath(taskSettings.getExternalProjectPath());
    runConfiguration.getSettings().setTaskNames(taskSettings.getTaskNames());
    
    
    ExecutionEnvironment env = new ExecutionEnvironment(runner, settings, project);
    
    try {
      runner.execute(executor, env, null);
    }
    catch (ExecutionException e) {
      LOG.warn("Can't execute task " + taskSettings, e);
    }
  }

  @Nullable
  public static AbstractExternalSystemTaskConfigurationType findConfigurationType(@NotNull ProjectSystemId externalSystemId) {
    for (ConfigurationType type : Extensions.getExtensions(ConfigurationType.CONFIGURATION_TYPE_EP)) {
      if (type instanceof AbstractExternalSystemTaskConfigurationType) {
        AbstractExternalSystemTaskConfigurationType candidate = (AbstractExternalSystemTaskConfigurationType)type;
        if (externalSystemId.equals(candidate.getExternalSystemId())) {
          return candidate;
        }
      }
    }
    return null;
  }

  /**
   * Is expected to be called when given task info is about to be executed.
   * <p/>
   * Basically, this method updates recent tasks list at the corresponding external system tool window and
   * persists new recent tasks state.
   * 
   * @param taskInfo  task which is about to be executed
   * @param project   target project
   */
  public static void updateRecentTasks(@NotNull ExternalTaskExecutionInfo taskInfo, @NotNull Project project) {
    ProjectSystemId externalSystemId = taskInfo.getSettings().getExternalSystemId();
    ExternalSystemRecentTasksList recentTasksList = getToolWindowElement(ExternalSystemRecentTasksList.class,
                                                                         project,
                                                                         ExternalSystemDataKeys.RECENT_TASKS_LIST,
                                                                         externalSystemId);
    if (recentTasksList == null) {
      return;
    }
    recentTasksList.setFirst(taskInfo);
    
    ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(externalSystemId);
    assert manager != null;
    AbstractExternalSystemLocalSettings settings = manager.getLocalSettingsProvider().fun(project);
    settings.setRecentTasks(recentTasksList.getModel().getTasks());
  }

  @Nullable
  public static String getRunnerId(@NotNull String executorId) {
    return RUNNER_IDS.get(executorId);
  }
  
  private interface TaskUnderProgress {
    void execute(@NotNull ProgressIndicator indicator);
  }
}
