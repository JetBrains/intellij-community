package com.intellij.packaging.elements;

/**
 * @author nik
 */
public class PackagingElementOutputKind {
  public static final PackagingElementOutputKind DIRECTORIES_WITH_CLASSES = new PackagingElementOutputKind(true, false);
  public static final PackagingElementOutputKind JAR_FILES = new PackagingElementOutputKind(false, true);
  public static final PackagingElementOutputKind OTHER = new PackagingElementOutputKind(false, false);
  private boolean myContainsDirectoriesWithClasses;
  private boolean myContainsJarFiles;

  public PackagingElementOutputKind(boolean containsDirectoriesWithClasses, boolean containsJarFiles) {
    myContainsDirectoriesWithClasses = containsDirectoriesWithClasses;
    myContainsJarFiles = containsJarFiles;
  }

  public boolean containsDirectoriesWithClasses() {
    return myContainsDirectoriesWithClasses;
  }

  public boolean containsJarFiles() {
    return myContainsJarFiles;
  }
}
