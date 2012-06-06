package org.jetbrains.jps.model.library;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;

/**
 * @author nik
 */
public interface JpsLibraryRoot extends JpsElement {
  @NotNull
  JpsLibraryRootType getRootType();

  @NotNull
  String getUrl();

  @NotNull
  JpsLibrary getLibrary();
}
