package org.jetbrains.jps.model.library;

/**
 * @author nik
 */
public class JpsLibraryRootType {
  public static final JpsLibraryRootType COMPILED = new JpsLibraryRootType(JpsOrderRootType.COMPILED, false);
  public static final JpsLibraryRootType SOURCES = new JpsLibraryRootType(JpsOrderRootType.SOURCES, false);
  private final boolean myJarDirectory;
  private final JpsOrderRootType myType;

  public JpsLibraryRootType(JpsOrderRootType type, boolean jarDirectory) {
    myType = type;
    myJarDirectory = jarDirectory;
  }

  public boolean isJarDirectory() {
    return myJarDirectory;
  }

  public JpsOrderRootType getType() {
    return myType;
  }
}
