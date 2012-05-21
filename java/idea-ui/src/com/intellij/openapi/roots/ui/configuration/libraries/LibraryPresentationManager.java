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
package com.intellij.openapi.roots.ui.configuration.libraries;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryKind;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Set;

/**
 * @author nik
 */
public abstract class LibraryPresentationManager {
  public static LibraryPresentationManager getInstance() {
    return ServiceManager.getService(LibraryPresentationManager.class);
  }

  @NotNull
  public abstract Icon getNamedLibraryIcon(@NotNull Library library, @Nullable StructureConfigurableContext context);

  @Nullable
  public abstract Icon getCustomIcon(@NotNull Library library, @Nullable StructureConfigurableContext context);

  @NotNull
  public abstract List<Icon> getCustomIcons(@NotNull Library library, @Nullable StructureConfigurableContext context);

  @NotNull
  public abstract List<String> getDescriptions(@NotNull Library library, StructureConfigurableContext context);

  @NotNull
  public abstract List<String> getDescriptions(@NotNull VirtualFile[] classRoots, Set<LibraryKind> excludedKinds);

  public abstract List<Library> getLibraries(@NotNull Set<LibraryKind> kinds, @NotNull Project project, @Nullable StructureConfigurableContext context);

  public abstract boolean isLibraryOfKind(@NotNull List<VirtualFile> files, @NotNull LibraryKind kind);

  public abstract boolean isLibraryOfKind(@NotNull Library library, @NotNull LibrariesContainer librariesContainer,
                                          @NotNull Set<? extends LibraryKind> acceptedKinds);
}
