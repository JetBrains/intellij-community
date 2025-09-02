// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model.task;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.serviceContainer.AlreadyDisposedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents id of the task enqueued to external API for execution.
 */
public final class ExternalSystemTaskId implements Serializable {

  private static final @NotNull AtomicLong COUNTER          = new AtomicLong();
  private static final          long       serialVersionUID = 1L;

  private final @NotNull ExternalSystemTaskType myType;
  private final @NotNull String                 myProjectId;
  private final @NotNull ProjectSystemId        myProjectSystemId;

  private final long myId;

  private ExternalSystemTaskId(@NotNull ProjectSystemId projectSystemId, @NotNull ExternalSystemTaskType type, @NotNull String projectId, long taskId) {
    myType = type;
    myProjectId = projectId;
    myProjectSystemId = projectSystemId;
    myId = taskId;
  }

  public @NotNull String getIdeProjectId() {
    return myProjectId;
  }

  public @NotNull ProjectSystemId getProjectSystemId() {
    return myProjectSystemId;
  }

  /**
   * Allows to retrieve distinct task id object of the given type.
   *
   * @param type     target task type
   * @param project  target ide project
   * @return         distinct task id object of the given type
   */
  public static @NotNull ExternalSystemTaskId create(@NotNull ProjectSystemId projectSystemId, @NotNull ExternalSystemTaskType type, @NotNull Project project) {
    return create(projectSystemId, type, getProjectId(project));
  }

  public static @NotNull ExternalSystemTaskId create(@NotNull ProjectSystemId projectSystemId, @NotNull ExternalSystemTaskType type, @NotNull String ideProjectId) {
    return new ExternalSystemTaskId(projectSystemId, type, ideProjectId, COUNTER.getAndIncrement());
  }

  public static @NotNull String getProjectId(@NotNull Project project) {
    return project.isDisposed() ? project.getName() : project.getName() + ":" + project.getLocationHash();
  }

  public @Nullable Project findProject() {
    final ProjectManager projectManager = ProjectManager.getInstance();
    for (Project project : projectManager.getOpenProjects()) {
      if (myProjectId.equals(getProjectId(project))) return project;
    }
    return null;
  }

  public @NotNull Project getProject() {
    ProgressManager.checkCanceled();
    Project project = findProject();
    if (project == null) {
      throw new AlreadyDisposedException(String.format("Project %s is closed", getIdeProjectId()));
    }
    return project;
  }

  public @NotNull ExternalSystemTaskType getType() {
    return myType;
  }

  public long getId() {
    return myId;
  }

  @Override
  public int hashCode() {
    return 31 * myType.hashCode() + Long.hashCode(myId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ExternalSystemTaskId that = (ExternalSystemTaskId)o;
    return myId == that.myId && myType == that.myType;
  }

  @Override
  public String toString() {
    return myType + ":" + myId;
  }
}
