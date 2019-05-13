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
package com.intellij.dvcs.repo;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Nadya Zabrodina
 */
public abstract class RepositoryImpl implements Repository {

  @NotNull private final Project myProject;
  @NotNull private final VirtualFile myRootDir;


  @NotNull protected volatile State myState;
  @Nullable protected volatile String myCurrentRevision;

  protected RepositoryImpl(@NotNull Project project,
                           @NotNull VirtualFile dir,
                           @NotNull Disposable parentDisposable) {
    myProject = project;
    myRootDir = dir;
    Disposer.register(parentDisposable, this);
  }

  @Override
  @NotNull
  public VirtualFile getRoot() {
    return myRootDir;
  }

  @Override
  @NotNull
  public String getPresentableUrl() {
    return getRoot().getPresentableUrl();
  }

  @Override
  public String toString() {
    return getPresentableUrl();
  }

  @Override
  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  @Override
  public State getState() {
    return myState;
  }


  @Override
  @Nullable
  public String getCurrentRevision() {
    return myCurrentRevision;
  }

  @Override
  public void dispose() {
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


