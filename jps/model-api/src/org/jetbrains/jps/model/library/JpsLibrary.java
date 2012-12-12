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
package org.jetbrains.jps.model.library;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsNamedElement;
import org.jetbrains.jps.model.JpsReferenceableElement;

import java.io.File;
import java.util.List;

/**
 * @author nik
 */
public interface JpsLibrary extends JpsNamedElement, JpsReferenceableElement<JpsLibrary> {

  @NotNull
  List<JpsLibraryRoot> getRoots(@NotNull JpsOrderRootType rootType);

  void addRoot(@NotNull String url, @NotNull JpsOrderRootType rootType);

  void addRoot(@NotNull File file, @NotNull JpsOrderRootType rootType);

  void addRoot(@NotNull String url, @NotNull JpsOrderRootType rootType, @NotNull JpsLibraryRoot.InclusionOptions options);

  void removeUrl(@NotNull String url, @NotNull JpsOrderRootType rootType);

  void delete();

  @NotNull
  JpsLibraryReference createReference();

  @NotNull
  JpsLibraryType<?> getType();

  @Nullable
  <P extends JpsElement>
  JpsTypedLibrary<P> asTyped(@NotNull JpsLibraryType<P> type);

  @NotNull
  JpsElement getProperties();

  List<File> getFiles(final JpsOrderRootType rootType);

  List<String> getRootUrls(final JpsOrderRootType rootType);
}
