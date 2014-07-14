/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author mike
 */
public abstract class FileStatusManager {
  public static FileStatusManager getInstance(Project project) {
    return project.getComponent(FileStatusManager.class);
  }

  public abstract FileStatus getStatus(@NotNull VirtualFile virtualFile);

  public abstract void fileStatusesChanged();
  public abstract void fileStatusChanged(VirtualFile file);

  public abstract void addFileStatusListener(@NotNull FileStatusListener listener);
  public abstract void addFileStatusListener(@NotNull FileStatusListener listener, @NotNull Disposable parentDisposable);
  public abstract void removeFileStatusListener(@NotNull FileStatusListener listener);

  /**
   * @deprecated Use getStatus(file).getText()} instead
   */
  public String getStatusText(@NotNull VirtualFile file){
    return getStatus(file).getText();
  }

  /**
   * @deprecated Use getStatus(file).getColor()} instead
   */
  public Color getStatusColor(@NotNull VirtualFile file){
    return getStatus(file).getColor();
  }

  public abstract Color getNotChangedDirectoryColor(@NotNull VirtualFile vf);
}
