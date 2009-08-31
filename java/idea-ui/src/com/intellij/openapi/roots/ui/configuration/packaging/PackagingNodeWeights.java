package com.intellij.openapi.roots.ui.configuration.packaging;

/**
 * @author nik
 */
public class PackagingNodeWeights {
  public static final double ARTIFACT = 0;
  public static final double DIRECTORY = 1;
  public static final double FILE = 2;
  public static final double ARCHIVE = FILE;
  public static final double MODULE = FILE;
  public static final double LIBRARY = FILE;


  private PackagingNodeWeights() {
  }
}
