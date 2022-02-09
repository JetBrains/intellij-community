// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.wizard.Step;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.platform.ProjectTemplate;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;

public class WizardContext extends UserDataHolderBase {
  /**
   * a project where the module should be added, can be null => the wizard creates a new project
   */
  @Nullable
  private final Project myProject;
  private final Disposable myDisposable;
  private Session mySessionId = null;
  private Path myProjectFileDirectory;
  private String myProjectName;
  private String myCompilerOutputDirectory;
  private Sdk myProjectJdk;
  private ProjectBuilder myProjectBuilder;
  /**
   * Stores project type builder in case if replaced by TemplateModuleBuilder
   */
  private ProjectBuilder myOriginalBuilder;
  private final List<Listener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private StorageScheme myProjectStorageFormat = StorageScheme.DIRECTORY_BASED;
  private ModulesProvider myModulesProvider;
  private boolean myProjectFileDirectorySetExplicitly;
  private String myDefaultModuleName = "untitled";

  public void setProjectStorageFormat(StorageScheme format) {
    myProjectStorageFormat = format;
  }

  /**
   * @deprecated useless
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public boolean isNewWizard() {
    return true;
  }

  public ModulesProvider getModulesProvider() {
    return myModulesProvider;
  }

  public void setModulesProvider(ModulesProvider modulesProvider) {
    myModulesProvider = modulesProvider;
  }

  public Disposable getDisposable() {
    return myDisposable;
  }

  public void setDefaultModuleName(String defaultModuleName) {
    myDefaultModuleName = defaultModuleName;
  }

  public String getDefaultModuleName() {
    return myDefaultModuleName;
  }

  public interface Listener {

    default void buttonsUpdateRequested() { }

    default void nextStepRequested() { }

    default void switchToRequested(@NotNull String placeId) { }

    default void switchToRequested(@NotNull String placeId, @NotNull Consumer<Step> configure) {
      switchToRequested(placeId);
    }
  }

  public WizardContext(@Nullable Project project, Disposable parentDisposable) {
    myProject = project;
    myDisposable = parentDisposable;
    if (myProject != null){
      myProjectJdk = ProjectRootManager.getInstance(myProject).getProjectSdk();
    }
    if (isNewWizard()) {
      mySessionId = Session.createRandomId();
    }
  }

  @Nullable
  public Project getProject() {
    return myProject;
  }

  public @NotNull String getProjectFileDirectory() {
    return getProjectDirectory().toString();
  }

  public @NotNull Path getProjectDirectory() {
    if (myProjectFileDirectory != null) {
      return myProjectFileDirectory;
    }
    return Paths.get(RecentProjectsManager.getInstance().suggestNewProjectLocation());
  }

  public boolean isProjectFileDirectorySet() {
    return myProjectFileDirectory != null;
  }

  public boolean isProjectFileDirectorySetExplicitly() {
    return myProjectFileDirectorySetExplicitly;
  }

  public void setProjectFileDirectory(@Nullable String value) {
    setProjectFileDirectory(value == null ? null : Paths.get(value), false);
  }

  public void setProjectFileDirectory(@Nullable Path projectFileDirectory, boolean explicitly) {
    myProjectFileDirectorySetExplicitly = explicitly;
    myProjectFileDirectory = projectFileDirectory == null ? null : projectFileDirectory.normalize();
  }

  public String getCompilerOutputDirectory() {
    return myCompilerOutputDirectory;
  }

  public void setCompilerOutputDirectory(final String compilerOutputDirectory) {
    myCompilerOutputDirectory = compilerOutputDirectory;
  }

  public String getProjectName() {
    return myProjectName;
  }

  public void setProjectName(String projectName) {
    myProjectName = projectName;
  }

  public boolean isCreatingNewProject() {
    return myProject == null;
  }

  /**
   * Useses to select presentable name for message bundle texts.
   * <br/>Message bundle examples:
   * <br/><code>sentence="New {0,choice,0#module|1#project}"</code> -> New project, New module
   * <br/><code>title="New {0,choice,0#Module|1#Project}"</code> -> New Project, New Module
   */
  public int isCreatingNewProjectInt() {
    return isCreatingNewProject() ? 1 : 0;
  }

  @Nullable
  public Icon getStepIcon() {
    return null;
  }

  public void requestWizardButtonsUpdate() {
    for (Listener listener : myListeners) {
      listener.buttonsUpdateRequested();
    }
  }

  public void requestNextStep() {
    for (Listener listener : myListeners) {
      listener.nextStepRequested();
    }
  }

  public void requestSwitchTo(@NotNull String placeId) {
    requestSwitchTo(placeId, __ -> {});
  }

  public void requestSwitchTo(@NotNull String placeId, @NotNull Consumer<Step> configure) {
    for (Listener listener : myListeners) {
      listener.switchToRequested(placeId, configure);
    }
  }

  public void addContextListener(Listener listener) {
    myListeners.add(listener);
  }

  public void setProjectJdk(Sdk jdk) {
    myProjectJdk = jdk;
  }

  public Sdk getProjectJdk() {
    return myProjectJdk;
  }

  @Nullable
  public ProjectBuilder getProjectBuilder() {
    return myProjectBuilder;
  }

  public void setProjectBuilder(@Nullable final ProjectBuilder projectBuilder) {
    myProjectBuilder = projectBuilder;
    myOriginalBuilder = myProjectBuilder;
  }

  public void setProjectTemplate(@Nullable ProjectTemplate projectTemplate) {
    if (projectTemplate != null) {
      myProjectBuilder = projectTemplate.createModuleBuilder();
    }
    else {
      myProjectBuilder = myOriginalBuilder;
    }
  }

  public String getPresentationName() {
    return IdeCoreBundle.message(myProject == null ? "project.new.wizard.project.identification" : "project.new.wizard.module.identification");
  }

  public StorageScheme getProjectStorageFormat() {
    return myProjectStorageFormat;
  }

  public Session getSessionId() {
    return mySessionId;
  }
}
