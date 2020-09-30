// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.ui;

public final class PackagingElementWeights {
  public static final int ARTIFACT = 100;
  public static final int DIRECTORY = 50;
  public static final int DIRECTORY_COPY = 40;
  public static final int EXTRACTED_DIRECTORY = 39;
  public static final int LIBRARY = 30;
  public static final int MODULE = 20;
  public static final int FACET = 10;
  public static final int FILE_COPY = 0;

  private PackagingElementWeights() {
  }
}
