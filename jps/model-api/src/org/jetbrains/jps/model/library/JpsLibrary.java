package org.jetbrains.jps.model.library;

import org.jetbrains.annotations.NotNull;
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

  @NotNull
  JpsElement getProperties();

  List<File> getFiles(final JpsOrderRootType rootType);
}
