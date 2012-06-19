package org.jetbrains.jps.model.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryCollection;
import org.jetbrains.jps.model.library.JpsLibraryType;

import java.util.List;

/**
 * @author nik
 */
public interface JpsModule extends JpsNamedElement, JpsReferenceableElement<JpsModule> {
  @NotNull
  JpsUrlList getContentRootsList();

  @NotNull
  JpsUrlList getExcludeRootsList();


  @NotNull
  List<? extends JpsModuleSourceRoot> getSourceRoots();

  @NotNull
  <P extends JpsElementProperties>
  JpsModuleSourceRoot addSourceRoot(@NotNull JpsModuleSourceRootType<P> rootType, @NotNull String url);

  @NotNull
  <P extends JpsElementProperties>
  JpsModuleSourceRoot addSourceRoot(@NotNull JpsModuleSourceRootType<P> rootType, @NotNull String url, @NotNull P properties);

  void removeSourceRoot(@NotNull JpsModuleSourceRootType rootType, @NotNull String url);

  JpsDependenciesList getDependenciesList();

  @NotNull
  JpsElementContainer getContainer();

  @NotNull
  JpsModuleReference createReference();

  @NotNull
  JpsLibrary addModuleLibrary(@NotNull JpsLibraryType<?> type, @NotNull String name);

  void addModuleLibrary(@NotNull JpsLibrary library);

  @NotNull
  JpsLibraryCollection getLibraryCollection();

  @NotNull
  JpsSdkReferencesTable getSdkReferencesTable();

  void delete();
}
