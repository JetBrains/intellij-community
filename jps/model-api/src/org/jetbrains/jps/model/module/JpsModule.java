package org.jetbrains.jps.model.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.library.*;

import java.util.List;

/**
 * @author nik
 */
public interface JpsModule extends JpsNamedElement, JpsReferenceableElement<JpsModule>, JpsCompositeElement {
  @NotNull
  JpsUrlList getContentRootsList();

  @NotNull
  JpsUrlList getExcludeRootsList();

  @NotNull
  List<JpsModuleSourceRoot> getSourceRoots();

  @NotNull
  <P extends JpsElementProperties, Type extends JpsModuleSourceRootType<P> & JpsElementTypeWithDefaultProperties<P>>
  JpsModuleSourceRoot addSourceRoot(@NotNull String url, @NotNull Type rootType);

  @NotNull
  <P extends JpsElementProperties>
  JpsModuleSourceRoot addSourceRoot(@NotNull String url, @NotNull JpsModuleSourceRootType<P> rootType, @NotNull P properties);

  void removeSourceRoot(@NotNull String url, @NotNull JpsModuleSourceRootType rootType);

  @NotNull
  <P extends JpsElementProperties>
  JpsFacet addFacet(@NotNull String name, @NotNull JpsFacetType<P> type, @NotNull P properties);

  @NotNull
  List<JpsFacet> getFacets();

  JpsDependenciesList getDependenciesList();

  @NotNull
  JpsModuleReference createReference();

  @NotNull
  <P extends JpsElementProperties, Type extends JpsLibraryType<P> & JpsElementTypeWithDefaultProperties<P>>
  JpsLibrary addModuleLibrary(@NotNull String name, @NotNull Type type);

  void addModuleLibrary(@NotNull JpsLibrary library);

  @NotNull
  JpsLibraryCollection getLibraryCollection();

  @NotNull
  JpsSdkReferencesTable getSdkReferencesTable();

  @Nullable
  JpsLibraryReference getSdkReference(@NotNull JpsSdkType<?> type);

  @Nullable
  <P extends JpsSdkProperties>
  JpsTypedLibrary<P> getSdk(@NotNull JpsSdkType<P> type);

  void delete();

  JpsProject getProject();
}
