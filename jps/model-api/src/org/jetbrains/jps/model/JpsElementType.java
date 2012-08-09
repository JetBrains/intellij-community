package org.jetbrains.jps.model;

/**
 * @author nik
 */
public abstract class JpsElementType<P extends JpsElement> {
  private final JpsElementChildRole<P> myPropertiesRole = new JpsElementChildRole<P>();

  public final JpsElementChildRole<P> getPropertiesRole() {
    return myPropertiesRole;
  }
}
