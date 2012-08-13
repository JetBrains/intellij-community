package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.library.*;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;

/**
 * @author nik
 */
public interface JpsGlobal extends JpsCompositeElement, JpsReferenceableElement<JpsGlobal> {
  @NotNull
  <P extends JpsElement, LibraryType extends JpsLibraryType<P> & JpsElementTypeWithDefaultProperties<P>>
  JpsLibrary addLibrary(@NotNull LibraryType libraryType, final @NotNull String name);

  <P extends JpsElement, SdkType extends JpsSdkType<P> & JpsElementTypeWithDefaultProperties<P>>
  JpsTypedLibrary<JpsSdk<P>> addSdk(@NotNull String name, @Nullable String homePath, @Nullable String versionString, @NotNull SdkType type);

  <P extends JpsElement>
  JpsTypedLibrary<JpsSdk<P>> addSdk(@NotNull String name, @Nullable String homePath, @Nullable String versionString,
                                    @NotNull JpsSdkType<P> type, @NotNull P properties);

  @NotNull
  JpsLibraryCollection getLibraryCollection();
}
