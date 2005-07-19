/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.module;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.AreaInstance;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Module extends ComponentManager, AreaInstance {
  Module[] EMPTY_ARRAY = new Module[0];

  VirtualFile getModuleFile();

  @NotNull String getModuleFilePath();

  @NotNull ModuleType getModuleType();

  @NotNull Project getProject();

  @NotNull String getName();

  boolean isDisposed();

  boolean isSavePathsRelative();

  void setSavePathsRelative(boolean b);

  void setOption(@NotNull String optionName, @NotNull String optionValue);

  @Nullable String getOptionValue(@NotNull String optionName);

  @NotNull PomModule getPom();
}
