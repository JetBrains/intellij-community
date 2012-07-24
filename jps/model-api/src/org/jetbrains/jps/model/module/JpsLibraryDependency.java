package org.jetbrains.jps.model.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryReference;

/**
 * @author nik
 */
public interface JpsLibraryDependency extends JpsDependencyElement {
  @NotNull
  JpsLibraryReference getLibraryReference();

  @Nullable
  JpsLibrary getLibrary();
}
