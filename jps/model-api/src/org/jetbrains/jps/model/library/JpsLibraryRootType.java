package org.jetbrains.jps.model.library;

/**
 * @author nik
 */
public class JpsLibraryRootType {
  public static final JpsLibraryRootType COMPILED = new JpsLibraryRootType(JpsOrderRootType.COMPILED, false, false);
  public static final JpsLibraryRootType SOURCES = new JpsLibraryRootType(JpsOrderRootType.SOURCES, false, false);
  private final boolean myJarDirectory;
  private final boolean myRecursive;
  private final JpsOrderRootType myType;

  public JpsLibraryRootType(JpsOrderRootType type, boolean jarDirectory, boolean recursive) {
    myJarDirectory = jarDirectory;
    myRecursive = recursive;
    myType = type;
  }

  public boolean isJarDirectory() {
    return myJarDirectory;
  }

  public JpsOrderRootType getType() {
    return myType;
  }

  public boolean isRecursive() {
    return myRecursive;
  }
}
