package org.jetbrains.jps.model.library;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsTypedElement;

/**
 * @author nik
 */
public interface JpsTypedLibrary<P extends JpsElement> extends JpsLibrary, JpsTypedElement<P> {
  @NotNull
  @Override
  JpsLibraryType<P> getType();
}
