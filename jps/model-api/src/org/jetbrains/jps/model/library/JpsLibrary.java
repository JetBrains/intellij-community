package org.jetbrains.jps.model.library;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsNamedElement;
import org.jetbrains.jps.model.JpsReferenceableElement;

import java.util.List;

/**
 * @author nik
 */
public interface JpsLibrary extends JpsNamedElement, JpsReferenceableElement<JpsLibrary> {

  @NotNull
  List<String> getUrls(@NotNull JpsLibraryRootType rootType);

  void addUrl(@NotNull String url, @NotNull JpsLibraryRootType rootType);

  void removeUrl(@NotNull String url, @NotNull JpsLibraryRootType rootType);

  void delete();

  @NotNull
  JpsLibraryReference createReference();
}
