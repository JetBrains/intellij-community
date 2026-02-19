// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions;

public final class ReformatCodeRunOptions implements LayoutCodeOptions {

  private boolean myRearrangeCode;
  private boolean myOptimizeImports;
  private TextRangeType myProcessingScope;

  public ReformatCodeRunOptions(TextRangeType processingScope) {
    myProcessingScope = processingScope;
  }

  public void setProcessingScope(TextRangeType processingScope) {
    myProcessingScope = processingScope;
  }

  @Override
  public boolean isOptimizeImports() {
    return myOptimizeImports;
  }

  @Override
  public boolean isRearrangeCode() {
    return myRearrangeCode;
  }

  public ReformatCodeRunOptions setRearrangeCode(boolean rearrangeCode) {
    myRearrangeCode = rearrangeCode;
    return this;
  }

  public ReformatCodeRunOptions setOptimizeImports(boolean optimizeImports) {
    myOptimizeImports = optimizeImports;
    return this;
  }

  @Override
  public TextRangeType getTextRangeType() {
    return myProcessingScope;
  }

}

