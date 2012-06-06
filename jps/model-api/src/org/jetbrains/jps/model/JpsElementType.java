package org.jetbrains.jps.model;

/**
 * @author nik
 */
public abstract class JpsElementType<P extends JpsElementProperties> {
  public abstract P createDefaultProperties();

  public abstract P createCopy(P properties);
}
