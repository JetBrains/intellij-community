// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.repo;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public abstract class RepositoryImpl implements Repository {

  private final @NotNull Project myProject;
  private final @NotNull VirtualFile myRootDir;

  private boolean myDisposed;

  protected RepositoryImpl(@NotNull Project project,
                           @NotNull VirtualFile dir) {
    myProject = project;
    myRootDir = dir;
  }

  protected RepositoryImpl(@NotNull Project project,
                           @NotNull VirtualFile dir,
                           @NotNull Disposable parentDisposable) {
    this(project, dir);
    Disposer.register(parentDisposable, this);
  }

  @Override
  public @NotNull VirtualFile getRoot() {
    return myRootDir;
  }

  @Override
  public @NotNull String getPresentableUrl() {
    return getRoot().getPresentableUrl();
  }

  @Override
  public String toString() {
    return getPresentableUrl();
  }

  @Override
  public @NotNull Project getProject() {
    return myProject;
  }

  @Override
  public boolean isFresh() {
    return getCurrentRevision() == null;
  }

  @Override
  public boolean isDisposed() {
    return myDisposed;
  }

  @Override
  public void dispose() {
    myDisposed = true;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Repository that = (Repository)o;

    if (!getProject().equals(that.getProject())) return false;
    if (!getRoot().equals(that.getRoot())) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = getProject().hashCode();
    result = 31 * result + (getRoot().hashCode());
    return result;
  }
}


