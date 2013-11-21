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
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
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
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.service.ImportCanceledException;
import com.intellij.openapi.externalSystem.service.execution.AbstractExternalSystemTaskConfigurationType;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemResolveProjectTask;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemIdeNotificationManager;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.service.project.PlatformFacade;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureHelper;
import com.intellij.openapi.externalSystem.service.project.manage.ModuleDataService;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.externalSystem.service.settings.ExternalSystemConfigLocator;
import com.intellij.openapi.externalSystem.service.task.ui.ExternalSystemRecentTasksList;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowEP;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
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
    RUNNER_IDS.put(DefaultRunExecutor.EXECUTOR_ID, ExternalSystemConstants.RUNNER_ID);
    RUNNER_IDS.put(DefaultDebugExecutor.EXECUTOR_ID, ExternalSystemConstants.DEBUG_RUNNER_ID);
  }

  private ExternalSystemUtil() {
  }

  public static void ensureToolWindowInitialized(@NotNull Project project, @NotNull ProjectSystemId externalSystemId) {
    ToolWindowManager manager = ToolWindowManager.getInstance(project);
    if (!(manager instanceof ToolWindowManagerEx)) {
      return;
    }
    ToolWindowManagerEx managerEx = (ToolWindowManagerEx)manager;
    String id = externalSystemId.getReadableName();
    ToolWindow window = manager.getToolWindow(id);
    if (window != null) {
      return;
    }
    ToolWindowEP[] beans = Extensions.getExtensions(ToolWindowEP.EP_NAME);
    for (final ToolWindowEP bean : beans) {
      if (id.equals(bean.id)) {
        managerEx.initToolWindow(bean);
      }
    }
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
   * Asks to refresh all external projects of the target external system linked to the given ide project.
   * <p/>
   * 'Refresh' here means 'obtain the most up-to-date version and apply it to the ide'. 
   *
   * @param project           target ide project
   * @param externalSystemId  target external system which projects should be refreshed
   * @param force             flag which defines if external project refresh should be performed if it's config is up-to-date
   */
  public static void refreshProjects(@NotNull final Project project, @NotNull final ProjectSystemId externalSystemId, boolean force) {
    refreshProjects(project, externalSystemId, force, ProgressExecutionMode.IN_BACKGROUND_ASYNC);
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
  public static void refreshProjects(@NotNull final Project project, @NotNull final ProjectSystemId externalSystemId, boolean force, @NotNull final ProgressExecutionMode progressExecutionMode) {
    ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(externalSystemId);
    if (manager == null) {
      return;
    }
    AbstractExternalSystemSettings<?, ?, ?> settings = manager.getSettingsProvider().fun(project);
    final Collection<? extends ExternalProjectSettings> projectsSettings = settings.getLinkedProjectsSettings();
    if (projectsSettings.isEmpty()) {
      return;
    }

    final ProjectDataManager projectDataManager = ServiceManager.getService(ProjectDataManager.class);
    final int[] counter = new int[1];

    ExternalProjectRefreshCallback callback = new ExternalProjectRefreshCallback() {

      @NotNull
      private final Set<String> myExternalModulePaths = ContainerUtilRt.newHashSet();

      @Override
      public void onSuccess(@Nullable final DataNode<ProjectData> externalProject) {
        if (externalProject == null) {
          return;
        }
        Collection<DataNode<ModuleData>> moduleNodes = ExternalSystemApiUtil.findAll(externalProject, ProjectKeys.MODULE);
        for (DataNode<ModuleData> node : moduleNodes) {
          myExternalModulePaths.add(node.getData().getLinkedExternalProjectPath());
        }
        ExternalSystemApiUtil.executeProjectChangeAction(true, new DisposeAwareProjectChange(project) {
          @Override
          public void execute() {
            ProjectRootManagerEx.getInstanceEx(project).mergeRootsChangesDuring(new Runnable() {
              @Override
              public void run() {
                projectDataManager.importData(externalProject.getKey(), Collections.singleton(externalProject), project, true);
              }
            });

            processOrphanProjectLibraries();
          }
        });
        if (--counter[0] <= 0) {
          processOrphanModules();
        }
      }

      @Override
      public void onFailure(@NotNull String errorMessage, @Nullable String errorDetails) {
        counter[0] = Integer.MAX_VALUE; // Don't process orphan modules if there was an error on refresh.
      }

      private void processOrphanModules() {
        if(ExternalSystemDebugEnvironment.DEBUG_ORPHAN_MODULES_PROCESSING) {
          LOG.info(String.format(
            "Checking for orphan modules. External paths returned by external system: '%s'", myExternalModulePaths
          ));
        }
        PlatformFacade platformFacade = ServiceManager.getService(PlatformFacade.class);
        List<Module> orphanIdeModules = ContainerUtilRt.newArrayList();
        String externalSystemIdAsString = externalSystemId.toString();

        for (Module module : platformFacade.getModules(project)) {
          String s = module.getOptionValue(ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY);
          String p = module.getOptionValue(ExternalSystemConstants.LINKED_PROJECT_PATH_KEY);
          if(ExternalSystemDebugEnvironment.DEBUG_ORPHAN_MODULES_PROCESSING) {
            LOG.info(String.format(
              "IDE module: EXTERNAL_SYSTEM_ID_KEY - '%s', LINKED_PROJECT_PATH_KEY - '%s'.", s, p
            ));
          }
          if (externalSystemIdAsString.equals(s) && !myExternalModulePaths.contains(p)) {
            orphanIdeModules.add(module);
            if(ExternalSystemDebugEnvironment.DEBUG_ORPHAN_MODULES_PROCESSING) {
              LOG.info(String.format(
                "External paths doesn't contain IDE module LINKED_PROJECT_PATH_KEY anymore => add to orphan IDE modules."
              ));
            }
          }
        }

        if (!orphanIdeModules.isEmpty()) {
          ruleOrphanModules(orphanIdeModules, project, externalSystemId);
        }
      }

      private void processOrphanProjectLibraries() {
        PlatformFacade platformFacade = ServiceManager.getService(PlatformFacade.class);
        List<Library> orphanIdeLibraries = ContainerUtilRt.newArrayList();

        LibraryTable projectLibraryTable = platformFacade.getProjectLibraryTable(project);
        for (Library library : projectLibraryTable.getLibraries()) {
          if (!ExternalSystemApiUtil.isExternalSystemLibrary(library, externalSystemId)) continue;
          if (ProjectStructureHelper.isOrphanProjectLibrary(library, platformFacade.getModules(project))) {
            orphanIdeLibraries.add(library);
          }
        }
        for (Library orphanIdeLibrary : orphanIdeLibraries) {
          projectLibraryTable.removeLibrary(orphanIdeLibrary);
        }
      }
    };

    Map<String, Long> modificationStamps = manager.getLocalSettingsProvider().fun(project).getExternalConfigModificationStamps();
    Set<String> toRefresh = ContainerUtilRt.newHashSet();
    for (ExternalProjectSettings setting : projectsSettings) {
      Long oldModificationStamp = modificationStamps.get(setting.getExternalProjectPath());
      long currentModificationStamp = getTimeStamp(setting.getExternalProjectPath(), externalSystemId);
      if (force || currentModificationStamp < 0 || oldModificationStamp == null || oldModificationStamp < currentModificationStamp) {
        toRefresh.add(setting.getExternalProjectPath());
      }
    }

    if (!toRefresh.isEmpty()) {
      counter[0] = toRefresh.size();
      for (String path : toRefresh) {
        refreshProject(project, externalSystemId, path, callback, false, progressExecutionMode);
      }
    }
  }

  private static long getTimeStamp(@NotNull String path, @NotNull ProjectSystemId externalSystemId) {
    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(path));
    if (vFile == null) {
      return -1;
    }
    for (ExternalSystemConfigLocator locator : ExternalSystemConfigLocator.EP_NAME.getExtensions()) {
      if (!externalSystemId.equals(locator.getTargetExternalSystemId())) {
        continue;
      }
      VirtualFile adjusted = locator.adjust(vFile);
      if (adjusted != null) {
        vFile = adjusted;
      }
    }
    return vFile.getTimeStamp();
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
   * @param project               target intellij project to use
   * @param externalProjectPath   path of the target gradle project's file
   * @param callback              callback to be notified on refresh result
   * @param isPreviewMode         flag that identifies whether gradle libraries should be resolved during the refresh
   * @return the most up-to-date gradle project (if any)
   */
  public static void refreshProject(@NotNull final Project project,
                                    @NotNull final ProjectSystemId externalSystemId,
                                    @NotNull final String externalProjectPath,
                                    @NotNull final ExternalProjectRefreshCallback callback,
                                    final boolean isPreviewMode,
                                    @NotNull final ProgressExecutionMode progressExecutionMode) {
    refreshProject(project, externalSystemId, externalProjectPath, callback, isPreviewMode, progressExecutionMode, true);
  }

  /**
   * Queries slave gradle process to refresh target gradle project.
   *
   * @param project               target intellij project to use
   * @param externalProjectPath   path of the target gradle project's file
   * @param callback              callback to be notified on refresh result
   * @param isPreviewMode         flag that identifies whether gradle libraries should be resolved during the refresh
   * @param reportRefreshError    prevent to show annoying error notification, e.g. if auto-import mode used
   * @return the most up-to-date gradle project (if any)
   */
  public static void refreshProject(@NotNull final Project project,
                                    @NotNull final ProjectSystemId externalSystemId,
                                    @NotNull final String externalProjectPath,
                                    @NotNull final ExternalProjectRefreshCallback callback,
                                    final boolean isPreviewMode,
                                    @NotNull final ProgressExecutionMode progressExecutionMode,
                                    final boolean reportRefreshError)
  {
    File projectFile = new File(externalProjectPath);
    final String projectName;
    if (projectFile.isFile()) {
      projectName = projectFile.getParentFile().getName();
    }
    else {
      projectName = projectFile.getName();
    }
    final TaskUnderProgress refreshProjectStructureTask = new TaskUnderProgress() {
      @SuppressWarnings({"ThrowableResultOfMethodCallIgnored", "IOResourceOpenedButNotSafelyClosed"})
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        if(project.isDisposed()) return;

        ExternalSystemResolveProjectTask task
          = new ExternalSystemResolveProjectTask(externalSystemId, project, externalProjectPath, isPreviewMode);

        task.execute(indicator, ExternalSystemTaskNotificationListener.EP_NAME.getExtensions());
        final Throwable error = task.getError();
        if (error == null) {
          long stamp = getTimeStamp(externalProjectPath, externalSystemId);
          if (stamp > 0) {
            ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(externalSystemId);
            assert manager != null;
            if(project.isDisposed()) return;
            manager.getLocalSettingsProvider().fun(project).getExternalConfigModificationStamps().put(externalProjectPath, stamp);
          }
          DataNode<ProjectData> externalProject = task.getExternalProject();
          callback.onSuccess(externalProject);
          return;
        }
        if(error instanceof ImportCanceledException) {
          // stop refresh task
          return;
        }
        String message = ExternalSystemApiUtil.buildErrorMessage(error);
        if (StringUtil.isEmpty(message)) {
          message = String.format(
            "Can't resolve %s project at '%s'. Reason: %s",
            externalSystemId.getReadableName(), externalProjectPath, message
          );
        }

        callback.onFailure(message, extractDetails(error));

        ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(externalSystemId);
        if(manager == null) {
          return;
        }
        AbstractExternalSystemSettings<?, ?, ?> settings = manager.getSettingsProvider().fun(project);
        ExternalProjectSettings projectSettings = settings.getLinkedProjectSettings(externalProjectPath);
        if (projectSettings == null || !reportRefreshError) {
          return;
        }
        ExternalSystemIdeNotificationManager notificationManager = ServiceManager.getService(ExternalSystemIdeNotificationManager.class);
        if (notificationManager != null) {
          notificationManager.processExternalProjectRefreshError(error, project, projectName, externalSystemId);
        }
      }
    };

    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        final String title;
        switch (progressExecutionMode) {
          case MODAL_SYNC:
            title = ExternalSystemBundle.message("progress.import.text", projectName, externalSystemId.getReadableName());
            new Task.Modal(project, title, true) {
              @Override
              public void run(@NotNull ProgressIndicator indicator) {
                refreshProjectStructureTask.execute(indicator);
              }
            }.queue();
            break;
          case IN_BACKGROUND_ASYNC:
            title = ExternalSystemBundle.message("progress.refresh.text", projectName, externalSystemId.getReadableName());
            new Task.Backgroundable(project, title) {
              @Override
              public void run(@NotNull ProgressIndicator indicator) {
                refreshProjectStructureTask.execute(indicator);
              }
            }.queue();
            break;
          case START_IN_FOREGROUND_ASYNC:
            title = ExternalSystemBundle.message("progress.refresh.text", projectName, externalSystemId.getReadableName());
            new Task.Backgroundable(project, title, true, PerformInBackgroundOption.DEAF) {
              @Override
              public void run(@NotNull ProgressIndicator indicator) {
                refreshProjectStructureTask.execute(indicator);
              }
            }.queue();
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
    RunnerAndConfigurationSettings settings = RunManager.getInstance(project).createRunConfiguration(name, configurationType.getFactory());
    ExternalSystemRunConfiguration runConfiguration = (ExternalSystemRunConfiguration)settings.getConfiguration();
    runConfiguration.getSettings().setExternalProjectPath(taskSettings.getExternalProjectPath());
    runConfiguration.getSettings().setTaskNames(taskSettings.getTaskNames());
    
    
    ExecutionEnvironment env = new ExecutionEnvironment(executor, runner, settings, project);
    
    try {
      runner.execute(env, null);
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

  /**
   * Allows to answer if given ide project has 1-1 mapping with the given external project, i.e. the ide project has been
   * imported from external system and no other external projects have been added.
   * <p/>
   * This might be necessary in a situation when project-level setting is changed (e.g. project name). We don't want to rename
   * ide project if it doesn't completely corresponds to the given ide project then.
   *
   * @param ideProject       target ide project
   * @param externalProject  target external project
   * @return                 <code>true</code> if given ide project has 1-1 mapping to the given external project;
   *                         <code>false</code> otherwise
   */
  public static boolean isOneToOneMapping(@NotNull Project ideProject, @NotNull DataNode<ProjectData> externalProject) {
    String linkedExternalProjectPath = null;
    for (ExternalSystemManager<?, ?, ?, ?, ?> manager : ExternalSystemApiUtil.getAllManagers()) {
      ProjectSystemId externalSystemId = manager.getSystemId();
      AbstractExternalSystemSettings systemSettings = ExternalSystemApiUtil.getSettings(ideProject, externalSystemId);
      Collection projectsSettings = systemSettings.getLinkedProjectsSettings();
      int linkedProjectsNumber = projectsSettings.size();
      if (linkedProjectsNumber > 1) {
        // More than one external project of the same external system type is linked to the given ide project.
        return false;
      }
      else if (linkedProjectsNumber == 1) {
        if (linkedExternalProjectPath == null) {
          // More than one external project of different external system types is linked to the current ide project.
          linkedExternalProjectPath = ((ExternalProjectSettings)projectsSettings.iterator().next()).getExternalProjectPath();
        }
        else {
          return false;
        }
      }
    }
    
    ProjectData projectData = externalProject.getData();
    if (linkedExternalProjectPath != null && !linkedExternalProjectPath.equals(projectData.getLinkedExternalProjectPath())) {
      // New external project is being linked.
      return false;
    }

    Set<String> externalModulePaths = ContainerUtilRt.newHashSet();
    for (DataNode<ModuleData> moduleNode : ExternalSystemApiUtil.findAll(externalProject, ProjectKeys.MODULE)) {
      externalModulePaths.add(moduleNode.getData().getLinkedExternalProjectPath());
    }
    externalModulePaths.remove(linkedExternalProjectPath);
    
    PlatformFacade platformFacade = ServiceManager.getService(PlatformFacade.class);
    for (Module module : platformFacade.getModules(ideProject)) {
      String path = module.getOptionValue(ExternalSystemConstants.LINKED_PROJECT_PATH_KEY);
      if (!StringUtil.isEmpty(path) && !externalModulePaths.remove(path)) {
        return false;
      }
    }
    return externalModulePaths.isEmpty();
  }

  /**
   * Tries to obtain external project info implied by the given settings and link that external project to the given ide project. 
   * 
   * @param externalSystemId         target external system
   * @param projectSettings          settings of the external project to link
   * @param project                  target ide project to link external project to
   * @param executionResultCallback  it might take a while to resolve external project info, that's why it's possible to provide
   *                                 a callback to be notified on processing result. It receives <code>true</code> if an external
   *                                 project has been successfully linked to the given ide project;
   *                                 <code>false</code> otherwise (note that corresponding notification with error details is expected
   *                                 to be shown to the end-user then)
   * @param isPreviewMode            flag which identifies if missing external project binaries should be downloaded
   * @param progressExecutionMode         identifies how progress bar will be represented for the current processing
   */
  @SuppressWarnings("UnusedDeclaration")
  public static void linkExternalProject(@NotNull final ProjectSystemId externalSystemId,
                                         @NotNull final ExternalProjectSettings projectSettings,
                                         @NotNull final Project project,
                                         @Nullable final Consumer<Boolean> executionResultCallback,
                                         boolean isPreviewMode,
                                         @NotNull final ProgressExecutionMode progressExecutionMode)
  {
    ExternalProjectRefreshCallback callback = new ExternalProjectRefreshCallback() {
      @SuppressWarnings("unchecked")
      @Override
      public void onSuccess(@Nullable final DataNode<ProjectData> externalProject) {
        if (externalProject == null) {
          if (executionResultCallback != null) {
            executionResultCallback.consume(false);
          }
          return;
        }
        AbstractExternalSystemSettings systemSettings = ExternalSystemApiUtil.getSettings(project, externalSystemId);
        Set<ExternalProjectSettings> projects = ContainerUtilRt.newHashSet(systemSettings.getLinkedProjectsSettings());
        projects.add(projectSettings);
        systemSettings.setLinkedProjectsSettings(projects);
        ensureToolWindowInitialized(project, externalSystemId);
        ExternalSystemApiUtil.executeProjectChangeAction(new DisposeAwareProjectChange(project) {
          @Override
          public void execute() {
            ProjectRootManagerEx.getInstanceEx(project).mergeRootsChangesDuring(new Runnable() {
              @Override
              public void run() {
                ProjectDataManager dataManager = ServiceManager.getService(ProjectDataManager.class);
                dataManager.importData(externalProject.getKey(), Collections.singleton(externalProject), project, true);
              }
            });
          }
        });
        if (executionResultCallback != null) {
          executionResultCallback.consume(true);
        }
      }

      @Override
      public void onFailure(@NotNull String errorMessage, @Nullable String errorDetails) {
        if (executionResultCallback != null) {
          executionResultCallback.consume(false);
        }
      }
    };
    refreshProject(project, externalSystemId, projectSettings.getExternalProjectPath(), callback, isPreviewMode, progressExecutionMode);
  }
  
  private interface TaskUnderProgress {
    void execute(@NotNull ProgressIndicator indicator);
  }
}
