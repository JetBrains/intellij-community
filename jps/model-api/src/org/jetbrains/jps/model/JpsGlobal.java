package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryCollection;
import org.jetbrains.jps.model.library.JpsLibraryType;

/**
 * @author nik
 */
public interface JpsGlobal extends JpsCompositeElement, JpsReferenceableElement<JpsGlobal> {
  @NotNull
  JpsLibrary addLibrary(@NotNull JpsLibraryType libraryType, final @NotNull String name);

  @NotNull
  JpsLibraryCollection getLibraryCollection();
}
