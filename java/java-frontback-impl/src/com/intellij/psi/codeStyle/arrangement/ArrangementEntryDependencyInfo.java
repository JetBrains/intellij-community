// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.arrangement;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ArrangementEntryDependencyInfo {

  private final @NotNull List<ArrangementEntryDependencyInfo> myDependentEntries = new ArrayList<>();
  
  private final @NotNull JavaElementArrangementEntry myAnchorEntry;

  public ArrangementEntryDependencyInfo(@NotNull JavaElementArrangementEntry entry) {
    myAnchorEntry= entry;
  }

  public void addDependentEntryInfo(@NotNull ArrangementEntryDependencyInfo info) {
    myDependentEntries.add(info);
  }
  
  public @NotNull List<ArrangementEntryDependencyInfo> getDependentEntriesInfos() {
    return myDependentEntries;
  }

  public @NotNull JavaElementArrangementEntry getAnchorEntry() {
    return myAnchorEntry;
  }

  @Override
  public String toString() {
    return myAnchorEntry.toString();
  }
}
