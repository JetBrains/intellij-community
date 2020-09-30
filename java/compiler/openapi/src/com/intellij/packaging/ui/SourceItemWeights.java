// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.ui;

public final class SourceItemWeights {
  public static final int ARTIFACTS_GROUP_WEIGHT = 200;
  public static final int ARTIFACT_WEIGHT = 150;
  public static final int MODULE_GROUP_WEIGHT = 100;
  public static final int MODULE_WEIGHT = 50;
  public static final int MODULE_OUTPUT_WEIGHT = 30;
  public static final int MODULE_SOURCE_WEIGHT = 25;
  public static final int LIBRARY_WEIGHT = 10;

  private SourceItemWeights() {
  }
}
