// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.arrangement;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Not thread-safe.
 */
public class DefaultArrangementEntry implements ArrangementEntry {

  private final @NotNull List<ArrangementEntry> myChildren = new ArrayList<>();

  private final @Nullable ArrangementEntry       myParent;
  private @Nullable List<ArrangementEntry> myDependencies;

  private final int     myStartOffset;
  private final int     myEndOffset;
  private final boolean myCanBeMatched;

  public DefaultArrangementEntry(@Nullable ArrangementEntry parent, int startOffset, int endOffset, boolean canBeMatched) {
    myCanBeMatched = canBeMatched;
    assert startOffset < endOffset;
    myParent = parent;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
  }

  @Override
  public @Nullable ArrangementEntry getParent() {
    return myParent;
  }

  @Override
  public @NotNull List<? extends ArrangementEntry> getChildren() {
    return myChildren;
  }

  public void addChild(@NotNull ArrangementEntry entry) {
    myChildren.add(entry);
  }

  @Override
  public @Nullable List<? extends ArrangementEntry> getDependencies() {
    return myDependencies;
  }

  public void addDependency(@NotNull ArrangementEntry dependency) {
    if (myDependencies == null) {
      myDependencies = new ArrayList<>();
    }
    myDependencies.add(dependency);
  }

  @Override
  public int getStartOffset() {
    return myStartOffset;
  }

  @Override
  public int getEndOffset() {
    return myEndOffset;
  }

  @Override
  public boolean canBeMatched() {
    return myCanBeMatched;
  }
}
