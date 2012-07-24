package org.jetbrains.jps.model.library;

import org.jetbrains.jps.model.JpsElementProperties;
import org.jetbrains.jps.model.JpsElementReference;

/**
 * @author nik
 */
public interface JpsTypedLibraryReference<P extends JpsElementProperties> extends JpsElementReference<JpsTypedLibrary<P>> {
}
