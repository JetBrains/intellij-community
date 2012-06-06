package org.jetbrains.jps.model.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.library.JpsLibrary;

import java.util.List;

/**
 * @author nik
 */
public interface DependenciesList extends JpsElement {
  @NotNull
  JpsModuleDependency addModuleDependency(@NotNull JpsModule module);

  @NotNull
  JpsLibraryDependency addLibraryDependency(@NotNull JpsLibrary libraryElement);

  @NotNull
  List<? extends JpsDependencyElement> getDependencies();
}
