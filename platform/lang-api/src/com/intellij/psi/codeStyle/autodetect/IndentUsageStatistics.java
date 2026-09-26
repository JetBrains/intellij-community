// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle.autodetect;

public interface IndentUsageStatistics {
  int getTotalLinesWithLeadingTabs();

  int getTotalLinesWithLeadingSpaces();

  IndentUsageInfo getKMostUsedIndentInfo(int k);

  int getTotalIndentSizesDetected();

  int getTimesIndentUsed(int indent);
}
