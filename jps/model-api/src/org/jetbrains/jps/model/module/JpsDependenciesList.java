package org.jetbrains.jps.model.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsSdkType;

import java.util.List;

/**
 * @author nik
 */
public interface JpsDependenciesList extends JpsElement {
  @NotNull
  JpsModuleDependency addModuleDependency(@NotNull JpsModule module);

  @NotNull
  JpsLibraryDependency addLibraryDependency(@NotNull JpsLibrary libraryElement);

  void addModuleSourceDependency();

  void addSdkDependency(@NotNull JpsSdkType<?> sdkType);

  @NotNull
  List<? extends JpsDependencyElement> getDependencies();
}
