package org.jetbrains.jps.model.library;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementTypeWithDefaultProperties;

import java.util.List;

/**
 * @author nik
 */
public interface JpsLibraryCollection {
  @NotNull
  <P extends JpsElement, LibraryType extends JpsLibraryType<P> & JpsElementTypeWithDefaultProperties<P>>
  JpsLibrary addLibrary(@NotNull String name, @NotNull LibraryType type);

  @NotNull
  <P extends JpsElement>
  JpsTypedLibrary<P> addLibrary(@NotNull String name, @NotNull JpsLibraryType<P> type, @NotNull P properties);

  @NotNull
  List<JpsLibrary> getLibraries();

  @NotNull
  <P extends JpsElement>
  Iterable<JpsTypedLibrary<P>> getLibraries(@NotNull JpsLibraryType<P> type);

  void addLibrary(@NotNull JpsLibrary library);

  @Nullable
  JpsLibrary findLibrary(@NotNull String name);

  @Nullable
  <E extends JpsElement> JpsTypedLibrary<E> findLibrary(@NotNull String name, @NotNull JpsLibraryType<E> type);
}
