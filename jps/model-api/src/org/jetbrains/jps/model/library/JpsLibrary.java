// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.library;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsNamedElement;
import org.jetbrains.jps.model.JpsReferenceableElement;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public interface JpsLibrary extends JpsNamedElement, JpsReferenceableElement<JpsLibrary> {

  @NotNull
  List<JpsLibraryRoot> getRoots(@NotNull JpsOrderRootType rootType);

  void addRoot(@NotNull String url, @NotNull JpsOrderRootType rootType);

  void addRoot(@NotNull File file, @NotNull JpsOrderRootType rootType);

  void addRoot(@NotNull String url, @NotNull JpsOrderRootType rootType, @NotNull JpsLibraryRoot.InclusionOptions options);

  void removeUrl(@NotNull String url, @NotNull JpsOrderRootType rootType);

  void delete();

  @Override
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

  @NotNull List<Path> getPaths(@NotNull JpsOrderRootType rootType);

  List<String> getRootUrls(final JpsOrderRootType rootType);
}
