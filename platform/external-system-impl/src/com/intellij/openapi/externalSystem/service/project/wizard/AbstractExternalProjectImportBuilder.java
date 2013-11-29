package com.intellij.openapi.externalSystem.service.project.wizard;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemResolveProjectTask;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.externalSystem.service.settings.AbstractImportFromExternalSystemControl;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.projectImport.ProjectImportBuilder;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * GoF builder for gradle-backed projects.
 * 
 * @author Denis Zhdanov
 * @since 8/1/11 1:29 PM
 */
@SuppressWarnings("MethodMayBeStatic")
public abstract class AbstractExternalProjectImportBuilder<C extends AbstractImportFromExternalSystemControl>
  extends ProjectImportBuilder<DataNode<ProjectData>>
{

  private static final Logger LOG = Logger.getInstance("#" + AbstractExternalProjectImportBuilder.class.getName());

  @NotNull private final ProjectDataManager            myProjectDataManager;
  @NotNull private final C                             myControl;
  @NotNull private final ProjectSystemId               myExternalSystemId;

  private DataNode<ProjectData> myExternalProjectNode;

  public AbstractExternalProjectImportBuilder(@NotNull ProjectDataManager projectDataManager,
                                              @NotNull C control,
                                              @NotNull ProjectSystemId externalSystemId)
  {
    myProjectDataManager = projectDataManager;
    myControl = control;
    myExternalSystemId = externalSystemId;
  }

  @Override
  public List<DataNode<ProjectData>> getList() {
    return Arrays.asList(myExternalProjectNode);
  }

  @Override
  public boolean isMarked(DataNode<ProjectData> element) {
    return true;
  }

  @Override
  public void setList(List<DataNode<ProjectData>> gradleProjects) {
  }

  @Override
  public void setOpenProjectSettingsAfter(boolean on) {
  }

  @NotNull
  public C getControl(@Nullable Project currentProject) {
    myControl.setCurrentProject(currentProject);
    return myControl;
  }

  public void prepare(@NotNull WizardContext context) {
    myControl.reset();
    String pathToUse = context.getProjectFileDirectory();
    myControl.setLinkedProjectPath(pathToUse);
    doPrepare(context);
  }

  protected abstract void doPrepare(@NotNull WizardContext context);

  @Override
  public List<Module> commit(final Project project,
                             ModifiableModuleModel model,
                             ModulesProvider modulesProvider,
                             ModifiableArtifactModel artifactModel)
  {
    project.putUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT, Boolean.TRUE);
    final DataNode<ProjectData> externalProjectNode = getExternalProjectNode();
    if (externalProjectNode != null) {
      beforeCommit(externalProjectNode, project);
    }
    StartupManager.getInstance(project).runWhenProjectIsInitialized(new Runnable() {
      @SuppressWarnings("unchecked")
      @Override
      public void run() {
        AbstractExternalSystemSettings systemSettings = ExternalSystemApiUtil.getSettings(project, myExternalSystemId);
        final ExternalProjectSettings projectSettings = getCurrentExternalProjectSettings();
        Set<ExternalProjectSettings> projects = ContainerUtilRt.newHashSet(systemSettings.getLinkedProjectsSettings());
        // add current importing project settings to linked projects settings or replace if similar already exist
        projects.remove(projectSettings);
        projects.add(projectSettings);

        systemSettings.copyFrom(myControl.getSystemSettings());
        systemSettings.setLinkedProjectsSettings(projects);

        if (externalProjectNode != null) {
          ExternalSystemUtil.ensureToolWindowInitialized(project, myExternalSystemId);
          ExternalSystemApiUtil.executeProjectChangeAction(new DisposeAwareProjectChange(project) {
            @Override
            public void execute() {
              ProjectRootManagerEx.getInstanceEx(project).mergeRootsChangesDuring(new Runnable() {
                @Override
                public void run() {
                  myProjectDataManager.importData(externalProjectNode.getKey(), Collections.singleton(externalProjectNode), project, true);
                  myExternalProjectNode = null;
                }
              });
            }
          });

          final Runnable resolveDependenciesTask = new Runnable() {
            @Override
            public void run() {
              String progressText = ExternalSystemBundle.message("progress.resolve.libraries", myExternalSystemId.getReadableName());
              ProgressManager.getInstance().run(
                new Task.Backgroundable(project, progressText, false) {
                  @Override
                  public void run(@NotNull final ProgressIndicator indicator) {
                    if(project.isDisposed()) return;
                    ExternalSystemResolveProjectTask task
                      = new ExternalSystemResolveProjectTask(myExternalSystemId, project, projectSettings.getExternalProjectPath(), false);
                    task.execute(indicator, ExternalSystemTaskNotificationListener.EP_NAME.getExtensions());
                    DataNode<ProjectData> projectWithResolvedLibraries = task.getExternalProject();
                    if (projectWithResolvedLibraries == null) {
                      return;
                    }

                    setupLibraries(projectWithResolvedLibraries, project);
                  }
                });
            }
          };
          UIUtil.invokeLaterIfNeeded(resolveDependenciesTask);
        }
      }
    });
    return Collections.emptyList();
  }

  @NotNull
  private ExternalProjectSettings getCurrentExternalProjectSettings() {
    ExternalProjectSettings result = myControl.getProjectSettings().clone();
    File externalProjectConfigFile = getExternalProjectConfigToUse(new File(result.getExternalProjectPath()));
    final String linkedProjectPath = FileUtil.toCanonicalPath(externalProjectConfigFile.getPath());
    assert linkedProjectPath != null;
    result.setExternalProjectPath(linkedProjectPath);
    return result;
  }

  protected abstract void beforeCommit(@NotNull DataNode<ProjectData> dataNode, @NotNull Project project);

  /**
   * The whole import sequence looks like below:
   * <p/>
   * <pre>
   * <ol>
   *   <li>Get project view from the gradle tooling api without resolving dependencies (downloading libraries);</li>
   *   <li>Allow to adjust project settings before importing;</li>
   *   <li>Create IJ project and modules;</li>
   *   <li>Ask gradle tooling api to resolve library dependencies (download the if necessary);</li>
   *   <li>Configure libraries used by the gradle project at intellij;</li>
   *   <li>Configure library dependencies;</li>
   * </ol>
   * </pre>
   * <p/>
   *
   * @param projectWithResolvedLibraries  gradle project with resolved libraries (libraries have already been downloaded and
   *                                      are available at file system under gradle service directory)
   * @param project                       current intellij project which should be configured by libraries and module library
   *                                      dependencies information available at the given gradle project
   */
  private void setupLibraries(@NotNull final DataNode<ProjectData> projectWithResolvedLibraries, final Project project) {
    ExternalSystemApiUtil.executeProjectChangeAction(new DisposeAwareProjectChange(project) {
      @Override
      public void execute() {
        ProjectRootManagerEx.getInstanceEx(project).mergeRootsChangesDuring(new Runnable() {
          @Override
          public void run() {
            if (ExternalSystemApiUtil.isNewProjectConstruction()) {
              // Clean existing libraries (if any).
              LibraryTable projectLibraryTable = ProjectLibraryTable.getInstance(project);
              if (projectLibraryTable == null) {
                LOG.warn(
                  "Can't resolve external dependencies of the target gradle project (" + project + "). Reason: project "
                  + "library table is undefined"
                );
                return;
              }
              LibraryTable.ModifiableModel model = projectLibraryTable.getModifiableModel();
              try {
                for (Library library : model.getLibraries()) {
                  model.removeLibrary(library);
                }
              }
              finally {
                model.commit();
              }
            }

            // Register libraries.
            myProjectDataManager.importData(Collections.<DataNode<?>>singletonList(projectWithResolvedLibraries), project, false);
          }
        });
      }
    });
  }

  @Nullable
  private File getProjectFile() {
    String path = myControl.getProjectSettings().getExternalProjectPath();
    return path == null ? null : new File(path);
  }

  /**
   * Asks current builder to ensure that target external project is defined.
   *
   * @param wizardContext             current wizard context
   * @throws ConfigurationException   if gradle project is not defined and can't be constructed
   */
  @SuppressWarnings("unchecked")
  public void ensureProjectIsDefined(@NotNull WizardContext wizardContext) throws ConfigurationException {
    final String externalSystemName = myExternalSystemId.getReadableName();
    File projectFile = getProjectFile();
    if (projectFile == null) {
      throw new ConfigurationException(ExternalSystemBundle.message("error.project.undefined"));
    }
    projectFile = getExternalProjectConfigToUse(projectFile);
    final Ref<ConfigurationException> error = new Ref<ConfigurationException>();
    final ExternalProjectRefreshCallback callback = new ExternalProjectRefreshCallback() {
      @Override
      public void onSuccess(@Nullable DataNode<ProjectData> externalProject) {
        myExternalProjectNode = externalProject;
      }

      @Override
      public void onFailure(@NotNull String errorMessage, @Nullable String errorDetails) {
        if (!StringUtil.isEmpty(errorDetails)) {
          LOG.warn(errorDetails);
        }
        error.set(new ConfigurationException(ExternalSystemBundle.message("error.resolve.with.reason", errorMessage),
                                             ExternalSystemBundle.message("error.resolve.generic")));
      }
    };

    final Project project = getProject(wizardContext);
    final File finalProjectFile = projectFile;
    final String externalProjectPath = FileUtil.toCanonicalPath(finalProjectFile.getAbsolutePath());
    final Ref<ConfigurationException> exRef = new Ref<ConfigurationException>();
    executeAndRestoreDefaultProjectSettings(project, new Runnable() {
      @Override
      public void run() {
        try {
          ExternalSystemUtil.refreshProject(
            project,
            myExternalSystemId,
            externalProjectPath,
            callback,
            true,
            ProgressExecutionMode.MODAL_SYNC
          );
        }
        catch (IllegalArgumentException e) {
          exRef.set(
            new ConfigurationException(e.getMessage(), ExternalSystemBundle.message("error.cannot.parse.project", externalSystemName)));
        }
      }
    });
    ConfigurationException ex = exRef.get();
    if (ex != null) {
      throw ex;
    }
    if (myExternalProjectNode == null) {
      ConfigurationException exception = error.get();
      if (exception != null) {
        throw exception;
      }
    }
    else {
      applyProjectSettings(wizardContext);
    }
  }

  @SuppressWarnings("unchecked")
  private void executeAndRestoreDefaultProjectSettings(@NotNull Project project, @NotNull Runnable task) {
    if (!project.isDefault()) {
      task.run();
      return;
    }

    AbstractExternalSystemSettings systemSettings = ExternalSystemApiUtil.getSettings(project, myExternalSystemId);
    Object systemStateToRestore = null;
    if (systemSettings instanceof PersistentStateComponent) {
      systemStateToRestore = ((PersistentStateComponent)systemSettings).getState();
    }
    systemSettings.copyFrom(myControl.getSystemSettings());
    Collection projectSettingsToRestore = systemSettings.getLinkedProjectsSettings();
    systemSettings.setLinkedProjectsSettings(Collections.singleton(getCurrentExternalProjectSettings()));
    try {
      task.run();
    }
    finally {
      if (systemStateToRestore != null) {
        ((PersistentStateComponent)systemSettings).loadState(systemStateToRestore);
      }
      else {
        systemSettings.setLinkedProjectsSettings(projectSettingsToRestore);
      }
    }
  }

  /**
   * Allows to adjust external project config file to use on the basis of the given value.
   * <p/>
   * Example: a user might choose a directory which contains target config file and particular implementation expands
   * that to a particular file under the directory.
   * 
   * @param file  base external project config file
   * @return      external project config file to use
   */
  @NotNull
  protected abstract File getExternalProjectConfigToUse(@NotNull File file);

  @Nullable
  public DataNode<ProjectData> getExternalProjectNode() {
    return myExternalProjectNode;
  }

  /**
   * Applies external system-specific settings like project files location etc to the given context.
   * 
   * @param context  storage for the project/module settings.
   */
  public void applyProjectSettings(@NotNull WizardContext context) {
    if (myExternalProjectNode == null) {
      assert false;
      return;
    }
    context.setProjectName(myExternalProjectNode.getData().getInternalName());
    context.setProjectFileDirectory(myExternalProjectNode.getData().getIdeProjectFileDirectoryPath());
    applyExtraSettings(context);
  }

  protected abstract void applyExtraSettings(@NotNull WizardContext context);

  /**
   * Allows to get {@link Project} instance to use. Basically, there are two alternatives -
   * {@link WizardContext#getProject() project from the current wizard context} and
   * {@link ProjectManager#getDefaultProject() default project}.
   *
   * @param wizardContext   current wizard context
   * @return                {@link Project} instance to use
   */
  @NotNull
  public Project getProject(@NotNull WizardContext wizardContext) {
    Project result = wizardContext.getProject();
    if (result == null) {
      result = ProjectManager.getInstance().getDefaultProject();
    }
    return result;
  }
}
