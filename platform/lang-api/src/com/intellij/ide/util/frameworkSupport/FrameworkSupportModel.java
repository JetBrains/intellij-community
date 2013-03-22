/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.ide.util.frameworkSupport;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public interface FrameworkSupportModel extends UserDataHolder {
  @Nullable
  Project getProject();

  @Nullable
  ModuleBuilder getModuleBuilder();

  boolean isFrameworkSelected(@NotNull @NonNls String providerId);

  void addFrameworkListener(@NotNull FrameworkSupportModelListener listener);

  void addFrameworkListener(@NotNull FrameworkSupportModelListener listener, @NotNull Disposable parentDisposable);

  void removeFrameworkListener(@NotNull FrameworkSupportModelListener listener);

  void setFrameworkComponentEnabled(@NotNull @NonNls String providerId, boolean enabled);

  void updateFrameworkLibraryComponent(@NotNull String providerId);

  FrameworkSupportConfigurable getFrameworkConfigurable(@NotNull @NonNls String providerId);

  @Nullable
  FrameworkSupportConfigurable findFrameworkConfigurable(@NotNull @NonNls String providerId);
}
