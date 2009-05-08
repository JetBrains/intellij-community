package com.intellij.packaging.ui;

/**
 * @author nik
 */
public class PackagingElementWeights {
  public static final double ARTIFACT = 0;
  public static final double DIRECTORY = 1;
  public static final double DIRECTORY_COPY = 2;
  public static final double LIBRARY = 3;
  public static final double MODULE = 4;
  public static final double FILE_COPY = 5;

  private PackagingElementWeights() {
  }
}
