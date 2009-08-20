package com.intellij.packaging.ui;

/**
 * @author nik
 */
public class PackagingElementWeights {
  public static final int ARTIFACT = 100;
  public static final int DIRECTORY = 50;
  public static final int DIRECTORY_COPY = 40;
  public static final int LIBRARY = 30;
  public static final int MODULE = 20;
  public static final int FACET = 10;
  public static final int FILE_COPY = 0;

  private PackagingElementWeights() {
  }
}
