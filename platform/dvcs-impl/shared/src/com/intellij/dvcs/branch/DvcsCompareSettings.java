// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.branch;

public interface DvcsCompareSettings {
  boolean shouldSwapSidesInCompareBranches();

  void setSwapSidesInCompareBranches(boolean value);
}
