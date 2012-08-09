package org.jetbrains.jps.model.library;

import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementChildRole;

/**
 * @author nik
 */
public abstract class JpsLibraryType<P extends JpsElement> {
  private final JpsElementChildRole<P> myPropertiesRole = new JpsElementChildRole<P>();

  public final JpsElementChildRole<P> getPropertiesRole() {
    return myPropertiesRole;
  }
}
