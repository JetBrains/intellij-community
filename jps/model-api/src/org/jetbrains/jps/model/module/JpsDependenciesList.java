package org.jetbrains.jps.model.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryReference;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;

import java.util.List;

/**
 * @author nik
 */
public interface JpsDependenciesList extends JpsElement {
  @NotNull
  JpsModuleDependency addModuleDependency(@NotNull JpsModule module);

  @NotNull
  JpsModuleDependency addModuleDependency(@NotNull JpsModuleReference moduleReference);

  @NotNull
  JpsLibraryDependency addLibraryDependency(@NotNull JpsLibrary libraryElement);

  @NotNull
  JpsLibraryDependency addLibraryDependency(@NotNull JpsLibraryReference libraryReference);

  void addModuleSourceDependency();

  void addSdkDependency(@NotNull JpsSdkType<?> sdkType);

  @NotNull
  List<JpsDependencyElement> getDependencies();

  void clear();
}
