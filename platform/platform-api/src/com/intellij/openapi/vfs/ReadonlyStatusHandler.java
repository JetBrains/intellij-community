/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.openapi.vfs;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public abstract class ReadonlyStatusHandler {
  public static boolean ensureFilesWritable(@NotNull Project project, @NotNull VirtualFile... files) {
    return !getInstance(project).ensureFilesWritable(files).hasReadonlyFiles();
  }

  public abstract static class OperationStatus {
    @NotNull
    public abstract VirtualFile[] getReadonlyFiles();

    public abstract boolean hasReadonlyFiles();

    @NotNull
    public abstract String getReadonlyFilesMessage();
  }

  public abstract OperationStatus ensureFilesWritable(@NotNull VirtualFile... files);

  public OperationStatus ensureFilesWritable(@NotNull Collection<VirtualFile> files) {
    return ensureFilesWritable(files.toArray(new VirtualFile[files.size()]));
  }

  public static ReadonlyStatusHandler getInstance(Project project) {
    return ServiceManager.getService(project, ReadonlyStatusHandler.class);
  }

}
