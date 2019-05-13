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
package com.intellij.openapi.externalSystem.psi.search;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
public class ExternalModuleBuildGlobalSearchScope extends DelegatingGlobalSearchScope {

  @NotNull
  private final String externalModulePath;

  public ExternalModuleBuildGlobalSearchScope(@NotNull final Project project,
                                              @NotNull GlobalSearchScope baseScope,
                                              @NotNull String externalModulePath) {
    super(new DelegatingGlobalSearchScope(baseScope) {
      @Nullable
      @Override
      public Project getProject() {
        return project;
      }
    });
    this.externalModulePath = externalModulePath;
  }

  @NotNull
  public String getExternalModulePath() {
    return externalModulePath;
  }
}
