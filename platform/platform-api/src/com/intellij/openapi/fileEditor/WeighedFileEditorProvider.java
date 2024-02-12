// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor;

public abstract class WeighedFileEditorProvider implements FileEditorProvider {

  double DEFAULT_WEIGHT = 1;

  /**
   * @return double value used for editor ascending ordering 
   */
  public double getWeight() { return DEFAULT_WEIGHT; }

  @Override
  public boolean isDumbAware() {
    return true;
  }
}
