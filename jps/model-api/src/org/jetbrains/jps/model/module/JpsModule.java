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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryCollection;
import org.jetbrains.jps.model.library.JpsLibraryType;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;
import org.jetbrains.jps.model.library.sdk.JpsSdkReference;

import java.util.List;

/**
 * @author nik
 */
public interface JpsModule extends JpsNamedElement, JpsReferenceableElement<JpsModule>, JpsCompositeElement {
  @NotNull
  JpsUrlList getContentRootsList();

  @NotNull
  JpsUrlList getExcludeRootsList();

  @NotNull
  List<JpsModuleSourceRoot> getSourceRoots();

  @NotNull
  <P extends JpsElement>
  Iterable<JpsTypedModuleSourceRoot<P>> getSourceRoots(@NotNull JpsModuleSourceRootType<P> type);

  @NotNull
  <P extends JpsElement>
  JpsModuleSourceRoot addSourceRoot(@NotNull String url, @NotNull JpsModuleSourceRootType<P> rootType);

  @NotNull
  <P extends JpsElement>
  JpsModuleSourceRoot addSourceRoot(@NotNull String url, @NotNull JpsModuleSourceRootType<P> rootType, @NotNull P properties);

  void addSourceRoot(@NotNull JpsModuleSourceRoot root);

  void removeSourceRoot(@NotNull String url, @NotNull JpsModuleSourceRootType rootType);

  JpsDependenciesList getDependenciesList();

  @NotNull
  JpsModuleReference createReference();

  @NotNull
  <P extends JpsElement, Type extends JpsLibraryType<P> & JpsElementTypeWithDefaultProperties<P>>
  JpsLibrary addModuleLibrary(@NotNull String name, @NotNull Type type);

  void addModuleLibrary(@NotNull JpsLibrary library);

  @NotNull
  JpsLibraryCollection getLibraryCollection();

  @NotNull
  JpsSdkReferencesTable getSdkReferencesTable();

  @Nullable
  <P extends JpsElement>
  JpsSdkReference<P> getSdkReference(@NotNull JpsSdkType<P> type);

  @Nullable
  <P extends JpsElement>
  JpsSdk<P> getSdk(@NotNull JpsSdkType<P> type);

  void delete();

  JpsProject getProject();

  @NotNull
  JpsModuleType<?> getModuleType();

  @NotNull
  JpsElement getProperties();

  @Nullable
  <P extends JpsElement> JpsTypedModule<P> asTyped(@NotNull JpsModuleType<P> type);
}
