package org.jetbrains.jps.model.library;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public interface JpsLibraryCollection {
  @NotNull
  JpsLibrary addLibrary(@NotNull JpsLibraryType<?> libraryType, @NotNull String name);

  @NotNull
  List<JpsLibrary> getLibraries();

  void addLibrary(@NotNull JpsLibrary library);
}
