package org.jetbrains.jps.model.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryReference;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;

/**
 * @author nik
 */
public interface JpsSdkDependency extends JpsDependencyElement {
  @NotNull
  JpsSdkType<?> getSdkType();

  @Nullable
  JpsLibrary resolveSdk();

  @Nullable
  JpsLibraryReference getSdkReference();

  boolean isInherited();
}
