package org.jetbrains.jps.model.artifact.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.library.JpsLibraryReference;

/**
 * @author nik
 */
public interface JpsLibraryFilesPackagingElement extends JpsPackagingElement {
  @NotNull
  JpsLibraryReference getLibraryReference();
}
