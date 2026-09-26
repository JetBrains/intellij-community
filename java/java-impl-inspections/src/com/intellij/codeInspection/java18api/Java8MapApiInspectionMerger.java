// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.java18api;

import com.intellij.codeInspection.ex.InspectionElementsMerger;
import org.jetbrains.annotations.NotNull;

public final class Java8MapApiInspectionMerger extends InspectionElementsMerger {
  private static final String COLLECTION_API_INSPECTION = "Java8CollectionsApi";
  private static final String REPLACE_MAP_GET_INSPECTION = "Java8ReplaceMapGet";

  @Override
  public @NotNull String getMergedToolName() {
    return Java8MapApiInspection.SHORT_NAME;
  }

  @Override
  public String @NotNull [] getSourceToolNames() {
    return new String[] {COLLECTION_API_INSPECTION, REPLACE_MAP_GET_INSPECTION};
  }
}
