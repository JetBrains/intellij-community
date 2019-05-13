/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.dvcs;

import com.intellij.dvcs.repo.RepositoryImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MockRepositoryImpl extends RepositoryImpl {

  protected MockRepositoryImpl(@NotNull Project project,
                               @NotNull VirtualFile dir,
                               @NotNull Disposable parentDisposable) {
    super(project, dir, parentDisposable);
  }

  @Nullable
  @Override
  public String getCurrentBranchName() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public AbstractVcs getVcs() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isFresh() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void update() {
  }

  @NotNull
  @Override
  public String toLogString() {
    throw new UnsupportedOperationException();
  }
}
