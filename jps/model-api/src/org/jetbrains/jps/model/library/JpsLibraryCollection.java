package org.jetbrains.jps.model.library;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElementProperties;
import org.jetbrains.jps.model.JpsElementTypeWithDefaultProperties;

import java.util.List;

/**
 * @author nik
 */
public interface JpsLibraryCollection {
  @NotNull
  <P extends JpsElementProperties, LibraryType extends JpsLibraryType<P> & JpsElementTypeWithDefaultProperties<P>>
  JpsLibrary addLibrary(@NotNull String name, @NotNull LibraryType type);

  @NotNull
  <P extends JpsElementProperties>
  JpsTypedLibrary<P> addLibrary(@NotNull String name, @NotNull JpsLibraryType<P> type, @NotNull P properties);

  @NotNull
  List<JpsLibrary> getLibraries();

  void addLibrary(@NotNull JpsLibrary library);

  @Nullable
  JpsLibrary findLibrary(@NotNull String name);
}
