// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.ex;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public abstract class ProjectManagerEx extends ProjectManager {
  public static ProjectManagerEx getInstanceEx() {
    return (ProjectManagerEx)ApplicationManager.getApplication().getService(ProjectManager.class);
  }

  public static @Nullable ProjectManagerEx getInstanceExIfCreated() {
    return (ProjectManagerEx)ProjectManager.getInstanceIfCreated();
  }

  /**
   * @deprecated Use {@link #newProject(Path, OpenProjectTask)}
   */
  @Deprecated
  public abstract @Nullable Project newProject(@Nullable String projectName, @NotNull String filePath, boolean useDefaultProjectSettings, boolean isDummy);

  /**
   * @deprecated Pass {@code projectName} using {@link OpenProjectTask#projectName}.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public final @Nullable Project newProject(@NotNull Path file, @Nullable String projectName, @NotNull OpenProjectTask options) {
    return newProject(file, projectName == null ? options : options.withProjectName(projectName));
  }

  /**
   * Creates project but not open it. Use this method only in a test mode or special cases like new project wizard.
   */
  public abstract @Nullable Project newProject(@NotNull Path file, @NotNull OpenProjectTask options);

  public abstract @Nullable Project openProject(@NotNull Path projectStoreBaseDir, @NotNull OpenProjectTask options);

  public abstract @NotNull CompletableFuture<@Nullable Project> openProjectAsync(@NotNull Path projectStoreBaseDir, @NotNull OpenProjectTask options);

  public abstract @NotNull Project loadProject(@NotNull Path path);

  public abstract boolean openProject(@NotNull Project project);

  @TestOnly
  public abstract boolean isDefaultProjectInitialized();

  public abstract boolean isProjectOpened(@NotNull Project project);

  public abstract boolean canClose(@NotNull Project project);

  /**
   * The project and the app settings will be not saved.
   */
  public abstract boolean forceCloseProject(@NotNull Project project);

  @ApiStatus.Internal
  public abstract boolean saveAndForceCloseProject(@NotNull Project project);

  // return true if successful
  public abstract boolean closeAndDisposeAllProjects(boolean checkCanClose);

  @ApiStatus.Internal
  public abstract @NotNull List<String> getAllExcludedUrls();
}
