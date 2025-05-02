// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.bazel.SourceSnapshot;
import org.jetbrains.jps.bazel.SourceSnapshotDelta;
import org.jetbrains.jps.dependency.NodeSource;

import java.util.HashSet;
import java.util.Set;

import static org.jetbrains.jps.javac.Iterators.*;

public final class SourceSnapshotDeltaImpl extends ElementSnapshotDeltaImpl<NodeSource> implements SourceSnapshotDelta {
  private static final String RECOMPILED_SOURCE_DIGEST = "";
  
  private final Set<NodeSource> myRecompileMarked;
  private boolean myIsWholeTargetRecompile;

  public SourceSnapshotDeltaImpl(SourceSnapshot base) {
    super(base);
    myRecompileMarked = new HashSet<>();
  }

  public SourceSnapshotDeltaImpl(SourceSnapshot pastSnapshot, SourceSnapshot presentSnapshot) {
    super(pastSnapshot, presentSnapshot);
    myIsWholeTargetRecompile = count(presentSnapshot.getElements()) == count(super.getModified());
    myRecompileMarked = myIsWholeTargetRecompile? Set.of() : collect(super.getModified(), new HashSet<>());
  }

  @Override
  public @NotNull SourceSnapshot getBaseSnapshot() {
    return (SourceSnapshot)super.getBaseSnapshot();
  }

  @Override
  public boolean hasChanges() {
    return myIsWholeTargetRecompile || !myRecompileMarked.isEmpty() || !isEmpty(getDeleted());
  }

  @Override
  public @NotNull Iterable<@NotNull NodeSource> getModified() {
    return myIsWholeTargetRecompile? getBaseSnapshot().getElements() : myRecompileMarked;
  }

  @Override
  public boolean isRecompile(@NotNull NodeSource src) {
    return myIsWholeTargetRecompile || myRecompileMarked.contains(src);
  }

  @Override
  public void markRecompile(@NotNull NodeSource src) {
    if (!myIsWholeTargetRecompile) {
      myRecompileMarked.add(src);
    }
  }

  @Override
  public boolean isRecompileAll() {
    return myIsWholeTargetRecompile;
  }

  @Override
  public void markRecompileAll() {
    myIsWholeTargetRecompile = true;
    if (!myRecompileMarked.isEmpty()) {
      myRecompileMarked.clear();
    }
  }

  @Override
  public SourceSnapshot asSnapshot() {
    SourceSnapshot baseSnapshot = getBaseSnapshot();
    return !hasChanges()? baseSnapshot : new SourceSnapshot() {
      @Override
      public @NotNull Iterable<@NotNull NodeSource> getElements() {
        return flat(getDeleted(), baseSnapshot.getElements());
      }

      @Override
      public @NotNull String getDigest(NodeSource src) {
        return isRecompile(src) || contains(getDeleted(), src)? RECOMPILED_SOURCE_DIGEST : baseSnapshot.getDigest(src);
      }
    };
  }
}
