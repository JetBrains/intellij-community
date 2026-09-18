// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl;

import com.intellij.configurationStore.Scheme_implKt;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfigurationVcsSupport;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectKt;
import com.intellij.util.PathUtil;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class RunConfigurationStorageUi extends ProjectFileStorageSelector {
  private static final Logger LOG = Logger.getInstance(RunConfigurationStorageUi.class);

  private final @NotNull Project myProject;
  private final @Nullable Runnable myOnModifiedRunnable;

  private RCStorageType myRCStorageTypeInitial;
  private @Nullable @SystemIndependent @NonNls String myFolderPathIfStoredInArbitraryFileInitial;

  private RCStorageType myRCStorageType;
  private @Nullable @SystemIndependent @NonNls String myFolderPathIfStoredInArbitraryFile;

  private @Nullable Boolean myDotIdeaStorageVcsIgnored = null; // used as cache; null means not initialized yet

  public RunConfigurationStorageUi(@NotNull Project project, @Nullable Runnable onModifiedRunnable) {
    super(project, ExecutionBundle.message("run.configuration.store.in"));
    if (project.isDefault()) LOG.error("Don't use RunConfigurationStorageUi for default project");

    myProject = project;
    myOnModifiedRunnable = onModifiedRunnable;
  }

  @Override
  protected void onStorageSelectionChanged(boolean selected) {
    if (selected) {
      setStorageTypeAndPathToTheBestPossibleState();
    }
    else {
      myRCStorageType = RCStorageType.Workspace;
      myFolderPathIfStoredInArbitraryFile = null;
    }

    if (myOnModifiedRunnable != null) {
      myOnModifiedRunnable.run();
    }
  }

  @Override
  protected @NotNull String getStoragePath() {
    return myRCStorageType == RCStorageType.DotIdeaFolder
           ? getDotIdeaStoragePath(myProject)
           : StringUtil.notNullize(myFolderPathIfStoredInArbitraryFile);
  }

  @Override
  protected @NotNull Collection<String> getSuggestedPaths(@NotNull String path) {
    Set<String> pathsToSuggest = new LinkedHashSet<>();
    if (getErrorIfBadFolderPathForStoringInArbitraryFile(myProject, path) == null) {
      pathsToSuggest.add(path);
    }
    if (myRCStorageTypeInitial == RCStorageType.ArbitraryFileInProject && myFolderPathIfStoredInArbitraryFileInitial != null) {
      pathsToSuggest.add(myFolderPathIfStoredInArbitraryFileInitial);
    }
    pathsToSuggest.add(getDotIdeaStoragePath(myProject));
    pathsToSuggest.addAll(getFolderPathsWithinProjectWhereRunConfigurationsStored(myProject));
    return pathsToSuggest;
  }

  @Override
  protected void onStoragePathChanged(@NotNull String path) {
    applyChangedStoragePath(path);
    if (myOnModifiedRunnable != null) {
      myOnModifiedRunnable.run();
    }
  }

  @Override
  protected @Nullable String getPathError(@NotNull String path) {
    return getErrorIfBadFolderPathForStoringInArbitraryFile(myProject, path);
  }

  @Override
  protected @NotNull FileChooserDescriptor createPathChooserDescriptor() {
    String dotIdeaStoragePath = getDotIdeaStoragePath(myProject);
    // `chooseFiles` is set to `true` to be able to select 'project.ipr' file in IPR-based projects; other files are not selectable
    return new FileChooserDescriptor(true, true, false, false, false, false) {
      @Override
      public boolean isFileSelectable(@Nullable VirtualFile file) {
        if (file == null) return false;
        if (file.getPath().equals(dotIdeaStoragePath)) return true;
        return file.isDirectory() &&
               super.isFileSelectable(file) &&
               !file.getPath().endsWith("/.idea") &&
               !file.getPath().contains("/.idea/") &&
               ReadAction.computeBlocking(() -> ProjectFileIndex.getInstance(myProject).isInContent(file));
      }
    }.withEnvironmentRestricted(true);
  }

  private void applyChangedStoragePath(String newPath) {
    if (newPath.equals(getDotIdeaStoragePath(myProject))) {
      myRCStorageType = RCStorageType.DotIdeaFolder;
      myFolderPathIfStoredInArbitraryFile = null;
    }
    else {
      myRCStorageType = RCStorageType.ArbitraryFileInProject;
      myFolderPathIfStoredInArbitraryFile = newPath;
    }
    validatePath();
  }

  @Override
  protected boolean isPathInvalid() {
    return myRCStorageType == RCStorageType.ArbitraryFileInProject &&
           getErrorIfBadFolderPathForStoringInArbitraryFile(myProject, myFolderPathIfStoredInArbitraryFile) != null;
  }

  private static @NonNls @NotNull String getFileNameByRCName(@NotNull String rcName) {
    return Scheme_implKt.getMODERN_NAME_CONVERTER().invoke(rcName) + ".run.xml";
  }

  @Contract("_,null -> !null")
  private static @Nullable String getErrorIfBadFolderPathForStoringInArbitraryFile(@NotNull Project project,
                                                                                   @Nullable @NonNls @SystemIndependent String path) {
    return getErrorIfBadFolderPath(project, path, getDotIdeaStoragePath(project),
                                  ExecutionBundle.message("run.configuration.storage.folder.dot.idea.forbidden", File.separator));
  }

  /**
   * @return full path to .idea/runConfigurations folder (for directory-based projects) or full path to the project.ipr file (for file-based projects)
   */
  private static @NonNls @NotNull String getDotIdeaStoragePath(@NotNull Project project) {
    // notNullize is to make inspections happy. Paths can't be null for non-default project
    return ProjectKt.isDirectoryBased(project)
           ? RunManagerImpl.getInstanceImpl(project).getDotIdeaRunConfigurationsPath$intellij_platform_execution_impl()
           : StringUtil.notNullize(project.getProjectFilePath());
  }

  private void setStorageTypeAndPathToTheBestPossibleState() {
    // all that tricky logic, see the flowchart from the issue description https://youtrack.jetbrains.com/issue/UX-1126

    // 1. If this RC had been shared before Run Configurations dialog was opened - use the state that was used before.
    // This handles the case when user opens shared RC for editing and clicks the 'Save to file' check box two times.
    if (myRCStorageTypeInitial == RCStorageType.DotIdeaFolder) {
      myRCStorageType = RCStorageType.DotIdeaFolder;
      myFolderPathIfStoredInArbitraryFile = null;
      return;
    }

    if (myRCStorageTypeInitial == RCStorageType.ArbitraryFileInProject) {
      myRCStorageType = RCStorageType.ArbitraryFileInProject;
      myFolderPathIfStoredInArbitraryFile = StringUtil.notNullize(myFolderPathIfStoredInArbitraryFileInitial);
      return;
    }

    // 2. For IPR-based projects keep using project.ipr file to store RCs by default
    if (!ProjectKt.isDirectoryBased(myProject)) {
      myRCStorageType = RCStorageType.DotIdeaFolder;
      myFolderPathIfStoredInArbitraryFile = null;
      return;
    }

    // Rider prefers project_base_dir/.run/ folder to .idea/runConfigurations/
    if (!PlatformUtils.isRider()) {
      // 3. If the project is not under VCS, keep using .idea/runConfigurations
      RunConfigurationVcsSupport vcsSupport = myProject.getService(RunConfigurationVcsSupport.class);
      if (!vcsSupport.hasActiveVcss(myProject)) {
        myRCStorageType = RCStorageType.DotIdeaFolder;
        myFolderPathIfStoredInArbitraryFile = null;
        return;
      }

      // 4. If .idea/runConfigurations is not excluded from VCS (e.g. not in .gitignore), then use it
      if (!isDotIdeaStorageVcsIgnored(vcsSupport)) {
        myRCStorageType = RCStorageType.DotIdeaFolder;
        myFolderPathIfStoredInArbitraryFile = null;
        return;
      }
    }

    // notNullize is to make inspections happy. Paths can't be null for non-default project
    VirtualFile baseDir = StandardFileSystems.local().findFileByPath(StringUtil.notNullize(myProject.getBasePath()));
    LOG.assertTrue(baseDir != null);

    // 5. If project base dir is not within project content, use .idea/runConfigurations
    // In Rider baseDir always not in the project content by design after migration to the new workspace model.
    if (!PlatformUtils.isRider() && !ProjectFileIndex.getInstance(myProject).isInContent(baseDir)) {
      myRCStorageType = RCStorageType.DotIdeaFolder;
      myFolderPathIfStoredInArbitraryFile = null;
      return;
    }

    // 6. If there are other RCs stored in arbitrary files (and all in the same folder) - suggest that folder
    Collection<String> otherFolders = getFolderPathsWithinProjectWhereRunConfigurationsStored(myProject);
    if (otherFolders.size() == 1) {
      myRCStorageType = RCStorageType.ArbitraryFileInProject;
      myFolderPathIfStoredInArbitraryFile = otherFolders.iterator().next();
      return;
    }

    // default is .../project_base_dir/.run/ folder
    myRCStorageType = RCStorageType.ArbitraryFileInProject;
    myFolderPathIfStoredInArbitraryFile = baseDir.getPath() + "/.run";
  }

  private boolean isDotIdeaStorageVcsIgnored(RunConfigurationVcsSupport vcsSupport) {
    if (myDotIdeaStorageVcsIgnored == null) {
      myDotIdeaStorageVcsIgnored = vcsSupport.isDirectoryVcsIgnored(myProject, getDotIdeaStoragePath(myProject));
    }
    return myDotIdeaStorageVcsIgnored.booleanValue();
  }

  private static Collection<String> getFolderPathsWithinProjectWhereRunConfigurationsStored(@NotNull Project project) {
    Set<String> result = new HashSet<>();
    for (RunnerAndConfigurationSettings settings : RunManager.getInstance(project).getAllSettings()) {
      String filePath = settings.getPathIfStoredInArbitraryFileInProject();
      // two conditions on the next line are effectively equivalent, this is to make inspections happy
      if (settings.isStoredInArbitraryFileInProject() && filePath != null) {
        result.add(PathUtil.getParentPath(filePath));
      }
    }
    return result;
  }

  public boolean isStoredInFile() {
    return myRCStorageType == RCStorageType.DotIdeaFolder || myRCStorageType == RCStorageType.ArbitraryFileInProject;
  }

  public boolean isModified() {
    if (myRCStorageType != myRCStorageTypeInitial) return true;
    if (myRCStorageType == RCStorageType.ArbitraryFileInProject &&
        !Objects.equals(myFolderPathIfStoredInArbitraryFileInitial, myFolderPathIfStoredInArbitraryFile)) {
      return true;
    }
    return false;
  }

  public void reset(@NotNull RunnerAndConfigurationSettings settings) {
    boolean isManagedRunConfiguration = settings.getConfiguration().getType().isManaged();

    myRCStorageType = settings.isStoredInArbitraryFileInProject()
                      ? RCStorageType.ArbitraryFileInProject
                      : settings.isStoredInDotIdeaFolder()
                        ? RCStorageType.DotIdeaFolder
                        : RCStorageType.Workspace;
    myFolderPathIfStoredInArbitraryFile = PathUtil.getParentPath(StringUtil.notNullize(settings.getPathIfStoredInArbitraryFileInProject()));

    myRCStorageTypeInitial = myRCStorageType;
    myFolderPathIfStoredInArbitraryFileInitial = myFolderPathIfStoredInArbitraryFile;

    resetStorageUi(myRCStorageType == RCStorageType.DotIdeaFolder || myRCStorageType == RCStorageType.ArbitraryFileInProject,
                   isManagedRunConfiguration);
  }

  public void apply(@NotNull RunnerAndConfigurationSettings settings) {
    apply(settings, true);
  }

  public void apply(@NotNull RunnerAndConfigurationSettings settings, boolean checkPathValidity) {
    switch (myRCStorageType) {
      case Workspace -> settings.storeInLocalWorkspace();
      case DotIdeaFolder -> settings.storeInDotIdeaFolder();
      case ArbitraryFileInProject -> {
        if (checkPathValidity && getErrorIfBadFolderPathForStoringInArbitraryFile(myProject, myFolderPathIfStoredInArbitraryFile) != null) {
          // don't apply incorrect UI to the model
        }
        else {
          // not sure the 'Template' prefix of the 'Template XXX.run.xml' file name should be localized.
          String name = settings.isTemplate() ? "Template " + settings.getType().getDisplayName() : settings.getName();
          String fileName = getFileNameByRCName(name);
          settings.storeInArbitraryFileInProject(myFolderPathIfStoredInArbitraryFile + "/" + fileName);
        }
      }
      default -> throw new IllegalStateException("Unexpected value: " + myRCStorageType);
    }
  }

  private enum RCStorageType {Workspace, DotIdeaFolder, ArbitraryFileInProject}
}
