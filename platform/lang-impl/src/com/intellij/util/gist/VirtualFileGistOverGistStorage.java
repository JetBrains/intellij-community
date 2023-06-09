/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util.gist;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.util.gist.storage.GistStorage;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.function.Supplier;

class VirtualFileGistOverGistStorage<Data> implements VirtualFileGist<Data> {
  private static final Logger LOG = Logger.getInstance(VirtualFileGistOverGistStorage.class);



  private final @NotNull GistStorage.Gist<Data> gist;

  private final @NotNull GistCalculator<Data> calculator;

  VirtualFileGistOverGistStorage(@NotNull GistStorage.Gist<Data> gist,
                                 @NotNull GistCalculator<Data> calculator) {
    this.gist = gist;
    this.calculator = calculator;
  }

  @Override
  public Data getFileData(@Nullable Project project,
                          @NotNull VirtualFile file) {
    return getOrCalculateAndCache(project, file, calculator).get();
  }

  @Override
  public @Nullable Supplier<Data> getUpToDateOrNull(@Nullable Project project,
                                                    @NotNull VirtualFile file) {
    return getOrCalculateAndCache(project, file, null);
  }

  /** if calculator is null => return cached value, if exists, or null, if not */
  @Contract("_, _, !null -> !null")
  private @Nullable Supplier<Data> getOrCalculateAndCache(@Nullable Project project,
                                                          @NotNull VirtualFile file,
                                                          @Nullable GistCalculator<Data> calculator) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    ProgressManager.checkCanceled();

    if (!(file instanceof VirtualFileWithId)) {
      if (calculator != null) {
        Data value = calculator.calcData(project, file);
        return () -> value;
      }
      else {
        return null;
      }
    }

    int expectedGistStamp = GistManagerImpl.getGistStamp(file);
    try {
      GistStorage.GistData<Data> gistData = gist.getProjectData(project, file, expectedGistStamp);
      if (gistData.hasData()) {
        return () -> gistData.data();
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }

    if (calculator != null) {
      Data value = calculator.calcData(project, file);
      cacheResult(expectedGistStamp, value, project, file);
      return () -> value;
    }
    else {
      return null;
    }
  }

  private void cacheResult(int gistStamp,
                           @Nullable Data result,
                           @Nullable Project project,
                           @NotNull VirtualFile file) {
    try {
      gist.putProjectData(project, file, result, gistStamp);
    }
    catch (ProcessCanceledException pce) {
      throw pce;
    }
    catch (Throwable e) {
      LOG.error("Can't cache gist[" + gist.id() + "]@[" + file + "] -- gist will be re-calculated again on next request", e);
    }
  }
}

