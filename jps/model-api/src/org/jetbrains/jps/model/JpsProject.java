package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryCollection;
import org.jetbrains.jps.model.library.JpsLibraryType;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleType;

import java.util.List;

/**
 * @author nik
 */
public interface JpsProject extends JpsCompositeElement, JpsReferenceableElement<JpsProject> {

  @NotNull
  JpsModule addModule(@NotNull JpsModuleType<?> moduleType, @NotNull String name);

  void addModule(@NotNull JpsModule module);

  @NotNull
  List<JpsModule> getModules();


  @NotNull
  JpsLibrary addLibrary(@NotNull JpsLibraryType<?> libraryType, @NotNull String name);

  @NotNull
  JpsLibraryCollection getLibraryCollection();
}
