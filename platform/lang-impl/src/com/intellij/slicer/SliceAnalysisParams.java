// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.slicer;

import com.intellij.analysis.AnalysisScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SliceAnalysisParams {
  /**
   * Direction of flow: true = backward; false = forward
   */
  public boolean dataFlowToThis = true;
  /**
   * show method calls or field access on the variable being analysed
   */
  public boolean showInstanceDereferences = true;
  public AnalysisScope scope;
  /**
   * If present filters the occurrences
   */
  public @Nullable SliceValueFilter valueFilter;

  public SliceAnalysisParams() {
  }

  public SliceAnalysisParams(@NotNull SliceAnalysisParams params) {
    this.dataFlowToThis = params.dataFlowToThis;
    this.showInstanceDereferences = params.showInstanceDereferences;
    this.scope = params.scope;
    this.valueFilter = params.valueFilter;
  }
}
