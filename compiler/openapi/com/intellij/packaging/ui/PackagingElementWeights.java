package com.intellij.packaging.ui;

/**
 * @author nik
 */
public class PackagingElementWeights {
  public static final int ARTIFACT = 0;
  public static final int DIRECTORY = 10;
  public static final int DIRECTORY_COPY = 20;
  public static final int LIBRARY = 30;
  public static final int MODULE = 40;
  public static final int FILE_COPY = 50;

  private PackagingElementWeights() {
  }
}
