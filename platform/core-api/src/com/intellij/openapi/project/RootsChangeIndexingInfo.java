// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

public abstract class RootsChangeIndexingInfo {

  public static final RootsChangeIndexingInfo TOTAL_REINDEX = new RootsChangeIndexingInfo() {
    @Override
    public String toString() {
      return "RootsChangeIndexingInfo.TOTAL_REINDEX";
    }
  };

  public static final RootsChangeIndexingInfo NO_INDEXING_NEEDED = new RootsChangeIndexingInfo() {
    @Override
    public String toString() {
      return "RootsChangeIndexingInfo.NO_INDEXING_NEEDED";
    }
  };
}
