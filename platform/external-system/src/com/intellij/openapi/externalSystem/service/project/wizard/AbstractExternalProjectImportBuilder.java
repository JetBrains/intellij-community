package com.intellij.openapi.externalSystem.service.project.wizard;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.LibraryData;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemResolveProjectTask;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.externalSystem.service.settings.AbstractExternalProjectConfigurable;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.projectImport.ProjectImportBuilder;
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
public abstract class AbstractExternalProjectImportBuilder<C extends AbstractExternalProjectConfigurable>
  extends ProjectImportBuilder<DataNode<ProjectData>>
{

  private static final Logger LOG = Logger.getInstance("#" + AbstractExternalProjectImportBuilder.class.getName());

  @NotNull private final ExternalSystemSettingsManager mySettingsManager;
  @NotNull private final ProjectDataManager            myProjectDataManager;
  @NotNull private final C                             myConfigurable;
  @NotNull private final ProjectSystemId               myExternalSystemId;

  private DataNode<ProjectData> myExternalProjectNode;

  public AbstractExternalProjectImportBuilder(@NotNull ExternalSystemSettingsManager settingsManager,
                                              @NotNull ProjectDataManager projectDataManager,
                                              @NotNull C configurable,
                                              @NotNull ProjectSystemId externalSystemId)
  {
    mySettingsManager = settingsManager;
    myProjectDataManager = projectDataManager;
    myConfigurable = configurable;
    myConfigurable.setAlwaysShowLinkedProjectControls(true);
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
  public C getConfigurable() {
    return myConfigurable;
  }

  @NotNull
  public ProjectSystemId getExternalSystemId() {
    return myExternalSystemId;
  }

  @NotNull
  public ExternalSystemSettingsManager getSettingsManager() {
    return mySettingsManager;
  }

  public void prepare(@NotNull WizardContext context) {
    myConfigurable.reset();
    String pathToUse = context.getProjectFileDirectory();
    myConfigurable.setLinkedExternalProjectPath(pathToUse);
    doPrepare(context);
  }

  protected abstract void doPrepare(@NotNull WizardContext context);

  @Override
  public List<Module> commit(final Project project,
                             ModifiableModuleModel model,
                             ModulesProvider modulesProvider,
                             ModifiableArtifactModel artifactModel)
  {
    System.setProperty(ExternalSystemConstants.NEWLY_IMPORTED_PROJECT, Boolean.TRUE.toString());
    final DataNode<ProjectData> externalProjectNode = getExternalProjectNode();
    if (externalProjectNode != null) {
      beforeCommit(externalProjectNode, project);
    }
    StartupManager.getInstance(project).runWhenProjectIsInitialized(new Runnable() {
      @Override
      public void run() {
        AbstractExternalSystemSettings settings = mySettingsManager.getSettings(project, myExternalSystemId);
        final String linkedProjectPath = myConfigurable.getLinkedExternalProjectPath();
        assert linkedProjectPath != null;
        settings.setLinkedExternalProjectPath(linkedProjectPath);
        settings.setUseAutoImport(myConfigurable.isUseAutoImport());
        onProjectInit(project);

        if (externalProjectNode != null) {
          ExternalSystemUtil.executeProjectChangeAction(project, myExternalSystemId, project, new Runnable() {
            @Override
            public void run() {
              ProjectRootManagerEx.getInstanceEx(project).mergeRootsChangesDuring(new Runnable() {
                @Override
                public void run() {
                  Collection<DataNode<ModuleData>> modules = ExternalSystemUtil.findAll(externalProjectNode, ProjectKeys.MODULE);
                  myProjectDataManager.importData(ProjectKeys.MODULE, modules, project, true);
                }
              }); 
            }
          });

          final Runnable resolveDependenciesTask = new Runnable() {
            @Override
            public void run() {
              String progressText
                = ExternalSystemBundle.message("progress.resolve.libraries", ExternalSystemUtil.toReadableName(myExternalSystemId));
              ProgressManager.getInstance().run(
                new Task.Backgroundable(project, progressText, false) {
                  @Override
                  public void run(@NotNull final ProgressIndicator indicator) {
                    ExternalSystemResolveProjectTask task
                      = new ExternalSystemResolveProjectTask(myExternalSystemId, project, linkedProjectPath, true);
                    task.execute(indicator);
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

  protected abstract void beforeCommit(@NotNull DataNode<ProjectData> dataNode, @NotNull Project project);

  protected abstract void onProjectInit(@NotNull Project project);

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
    Collection<DataNode<LibraryData>> libraries = ExternalSystemUtil.findAll(projectWithResolvedLibraries, ProjectKeys.LIBRARY);
    ExternalSystemUtil.executeProjectChangeAction(project, projectWithResolvedLibraries.getData().getOwner(), libraries, new Runnable() {
      @Override
      public void run() {
        ProjectRootManagerEx.getInstanceEx(project).mergeRootsChangesDuring(new Runnable() {
          @Override
          public void run() {
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

            // Register libraries.
            Set<DataNode<?>> toImport = ContainerUtilRt.newHashSet();
            toImport.add(projectWithResolvedLibraries);
            myProjectDataManager.importData(toImport, project, false);
            // TODO den uncomment
            //ProjectStructureChangesModel changesModel = ServiceManager.getService(project, ProjectStructureChangesModel.class);
            //changesModel.update(projectWithResolvedLibraries);
          }
        });
        // TODO den uncomment
        //ServiceManager.getService(project, UserProjectChangesCalculator.class).updateCurrentProjectState();
      }
    });
  }

  @Nullable
  private File getProjectFile() {
    String path = myConfigurable.getLinkedExternalProjectPath();
    return path == null ? null : new File(path);
  }

  /**
   * Is called to indicate that user changed path to the gradle project to import.
   * 
   * @param path      new directory path
   */
  public void setCurrentProjectPath(@NotNull String path) {
    myConfigurable.setLinkedExternalProjectPath(path);
  }

  /**
   * Asks current builder to ensure that target gradle project is defined.
   *
   * @param wizardContext             current wizard context
   * @throws ConfigurationException   if gradle project is not defined and can't be constructed
   */
  public void ensureProjectIsDefined(@NotNull WizardContext wizardContext) throws ConfigurationException {
    String externalSystemName = ExternalSystemUtil.toReadableName(myExternalSystemId);
    File projectFile = getProjectFile();
    if (projectFile == null) {
      throw new ConfigurationException(ExternalSystemBundle.message("error.project.undefined"));
    }
    projectFile = getExternalProjectConfigToUse(projectFile);
    final Ref<String> errorReason = new Ref<String>();
    final Ref<String> errorDetails = new Ref<String>();
    try {
      final Project project = getProject(wizardContext);
      myExternalProjectNode= ExternalSystemUtil.refreshProject(project, myExternalSystemId, projectFile.getAbsolutePath(), errorReason,
                                                               errorDetails, false, true);
    }
    catch (IllegalArgumentException e) {
      throw new ConfigurationException(e.getMessage(), ExternalSystemBundle.message("error.cannot.parse.project", externalSystemName));
    }
    if (myExternalProjectNode == null) {
      final String details = errorDetails.get();
      if (!StringUtil.isEmpty(details)) {
        LOG.warn(details);
      }
      String errorMessage;
      String reason = errorReason.get();
      if (reason == null) {
        errorMessage = ExternalSystemBundle.message("error.resolve.generic.without.reason", externalSystemName, projectFile.getPath());
      }
      else {
        errorMessage = ExternalSystemBundle.message("error.resolve.with.reason", reason);
      }
      throw new ConfigurationException(errorMessage, ExternalSystemBundle.message("error.resolve.generic"));
    }
    else {
      applyProjectSettings(wizardContext);
    }
  }

  // TODO den add doc
  protected abstract File getExternalProjectConfigToUse(@NotNull File file);

  @Nullable
  public DataNode<ProjectData> getExternalProjectNode() {
    return myExternalProjectNode;
  }

  @Override
  public boolean isSuitableSdkType(SdkTypeId sdk) {
    return sdk == JavaSdk.getInstance();
  }

  /**
   * Applies gradle-plugin-specific settings like project files location etc to the given context.
   * 
   * @param context  storage for the project/module settings.
   */
  public void applyProjectSettings(@NotNull WizardContext context) {
    if (myExternalProjectNode == null) {
      assert false;
      return;
    }
    context.setProjectName(myExternalProjectNode.getData().getName());
    context.setProjectFileDirectory(myExternalProjectNode.getData().getProjectFileDirectoryPath());
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
