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
package com.intellij.diff;

import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.merge.MergeRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public abstract class DiffManager {
  @NotNull
  public static DiffManager getInstance() {
    return ServiceManager.getService(DiffManager.class);
  }

  //
  // Usage
  //

  @CalledInAwt
  public abstract void showDiff(@Nullable Project project, @NotNull DiffRequest request);

  @CalledInAwt
  public abstract void showDiff(@Nullable Project project, @NotNull DiffRequest request, @NotNull DiffDialogHints hints);

  @CalledInAwt
  public abstract void showDiff(@Nullable Project project, @NotNull DiffRequestChain requests, @NotNull DiffDialogHints hints);

  @NotNull
  public abstract DiffRequestPanel createRequestPanel(@Nullable Project project, @NotNull Disposable parent, @Nullable Window window);

  @CalledInAwt
  public abstract void showMerge(@Nullable Project project, @NotNull MergeRequest request);
}
