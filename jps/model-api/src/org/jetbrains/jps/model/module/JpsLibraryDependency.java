package org.jetbrains.jps.model.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.library.JpsLibraryReference;

/**
 * @author nik
 */
public interface JpsLibraryDependency extends JpsDependencyElement {
  @NotNull
  JpsLibraryReference getLibraryReference();
}
