// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle.autodetect;

public final class IndentUsageInfo {
  private final int indentSize;
  private final int timesUsed;

  public IndentUsageInfo(int indentSize, int timesUsed) {
    this.indentSize = indentSize;
    this.timesUsed = timesUsed;
  }

  public int getIndentSize() {
    return indentSize;
  }

  public int getTimesUsed() {
    return timesUsed;
  }

  @Override
  public String toString() {
    return "indent: " + indentSize + ", used " + timesUsed;
  }
}
