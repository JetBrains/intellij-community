/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.fileChooser;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface FileChooserDialog {
  DataKey<Boolean> PREFER_LAST_OVER_TO_SELECT = PathChooserDialog.PREFER_LAST_OVER_EXPLICIT;

  /**
   * @deprecated Please use {@link #choose(com.intellij.openapi.project.Project, com.intellij.openapi.vfs.VirtualFile...)} because
   * it supports several selections
   */
  @Deprecated
  @NotNull
  VirtualFile[] choose(@Nullable VirtualFile toSelect, @Nullable Project project);

  /**
   * Choose one or more files
   *
   * @param project  use this project (you may pass null if you already set project in ctor)
   * @param toSelect files to be selected automatically.
   * @return files chosen by user
   */
  @NotNull
  VirtualFile[] choose(@Nullable Project project, @NotNull VirtualFile... toSelect);
}