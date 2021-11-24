/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This extension point allows resolve subsystem to modify existing resolve scope for the particular {@link VirtualFile} by specifying
 * {@link SearchScope} which should be added to the existing resolve scope.
 * For example, {@link com.intellij.ide.scratch.ScratchResolveScopeEnlarger} adds current scratch file to the standard resolve scope
 * to be able to resolve stuff inside scratch file even if it's outside the project roots.
 */
public abstract class ResolveScopeEnlarger {
  public static final ExtensionPointName<ResolveScopeEnlarger> EP_NAME = ExtensionPointName.create("com.intellij.resolveScopeEnlarger");

  @Nullable
  public abstract SearchScope getAdditionalResolveScope(@NotNull VirtualFile file, @NotNull Project project);
}
