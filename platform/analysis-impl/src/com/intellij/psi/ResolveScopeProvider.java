/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This extension point allows resolve subsystem to define custom {@link GlobalSearchScope} this particular {@link VirtualFile} should be resolved in.
 * By default, this scope consists of the current module with all its dependencies, but sometimes it should be something completely different.
 * To add some scope to the existing resolve scope it may be easier to use {@link ResolveScopeEnlarger} instead.
 * @see ResolveScopeEnlarger
 */
public abstract class ResolveScopeProvider {
  public static final ExtensionPointName<ResolveScopeProvider> EP_NAME = ExtensionPointName.create("com.intellij.resolveScopeProvider");

  /**
   * @return {@link GlobalSearchScope} which defines destination scope where to resolve is allowed from given {@code invocationPoint}.
   */
  @Nullable
  public abstract GlobalSearchScope getResolveScope(@NotNull VirtualFile file, @NotNull Project project);
}
