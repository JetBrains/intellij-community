package org.jetbrains.jps.model.library;

/**
 * @author nik
 */
public abstract class JpsOrderRootType {
  public static final JpsOrderRootType COMPILED = new JpsOrderRootType() {
  };
  public static final JpsOrderRootType SOURCES = new JpsOrderRootType() {
  };
  public static final JpsOrderRootType DOCUMENTATION = new JpsOrderRootType() {
  };
}
