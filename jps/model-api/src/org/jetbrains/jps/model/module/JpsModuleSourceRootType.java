package org.jetbrains.jps.model.module;

import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementChildRole;

/**
 * @author nik
 */
public abstract class JpsModuleSourceRootType<P extends JpsElement> {
  private final JpsElementChildRole<P> myPropertiesRole = new JpsElementChildRole<P>();

  public final JpsElementChildRole<P> getPropertiesRole() {
    return myPropertiesRole;
  }
}
