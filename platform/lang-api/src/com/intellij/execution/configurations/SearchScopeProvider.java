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
package com.intellij.execution.configurations;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;

/**
 * @author Vojtech Krasa
 * @deprecated Use {@link GlobalSearchScopes}
 */
@Deprecated
public class SearchScopeProvider {

  /** @deprecated Use {@link GlobalSearchScopes#executionScope(Collection)}*/
  @Deprecated
  @NotNull
  public static GlobalSearchScope createSearchScope(@NotNull Project project, @Nullable RunProfile runProfile) {
    return GlobalSearchScopes.executionScope(project, runProfile);
  }

  /** @deprecated Use {@link GlobalSearchScopes#executionScope(Collection)}*/
  @Deprecated
  @Nullable
  public static GlobalSearchScope createSearchScope(@NotNull Module[] modules) {
    return GlobalSearchScopes.executionScope(Arrays.asList(modules));
  }
}
