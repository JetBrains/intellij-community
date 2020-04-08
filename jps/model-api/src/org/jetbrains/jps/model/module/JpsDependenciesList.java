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
package org.jetbrains.jps.model.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryReference;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;

import java.util.List;

public interface JpsDependenciesList extends JpsElement {
  @NotNull
  JpsModuleDependency addModuleDependency(@NotNull JpsModule module);

  @NotNull
  JpsModuleDependency addModuleDependency(@NotNull JpsModuleReference moduleReference);

  @NotNull
  JpsLibraryDependency addLibraryDependency(@NotNull JpsLibrary libraryElement);

  @NotNull
  JpsLibraryDependency addLibraryDependency(@NotNull JpsLibraryReference libraryReference);

  void addModuleSourceDependency();

  void addSdkDependency(@NotNull JpsSdkType<?> sdkType);

  @NotNull
  List<JpsDependencyElement> getDependencies();

  void clear();
}
