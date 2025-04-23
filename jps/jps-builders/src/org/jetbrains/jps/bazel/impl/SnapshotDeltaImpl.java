// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.bazel.SourceSnapshot;
import org.jetbrains.jps.bazel.SourceSnapshotDelta;
import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.jps.dependency.diff.Difference;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static org.jetbrains.jps.javac.Iterators.*;

public final class SnapshotDeltaImpl implements SourceSnapshotDelta {
  private static final String RECOMPILED_SOURCE_DIGEST = "";
  
  private final SourceSnapshot myBaseSnapshot;
  private final Set<NodeSource> myDeleted = new HashSet<>();
  private final Set<NodeSource> myRecompileMarked = new HashSet<>();
  private boolean myIsWholeTargetRecompile;

  public SnapshotDeltaImpl(SourceSnapshot base) {
    myBaseSnapshot = base;
  }

  public SnapshotDeltaImpl(SourceSnapshot pastSnapshot, SourceSnapshot presentSnapshot) {
    this(presentSnapshot);
    if (!isEmpty(pastSnapshot.getSources())) {
      Difference.Specifier<@NotNull NodeSource, Difference> diff = Difference.deepDiff(
        pastSnapshot.getSources(), presentSnapshot.getSources(), NodeSource::equals, NodeSource::hashCode, (pastSrc, presentSrc) -> () -> Objects.equals(pastSnapshot.getDigest(pastSrc), presentSnapshot.getDigest(presentSrc))
      );
      collect(diff.removed(), myDeleted);
      collect(flat(map(diff.changed(), Difference.Change::getPast), diff.added()), myRecompileMarked);
    }
    else {
      myIsWholeTargetRecompile = true;
    }
  }

  @Override
  public @NotNull SourceSnapshot getBaseSnapshot() {
    return myBaseSnapshot;
  }

  @Override
  public boolean hasChanges() {
    return myIsWholeTargetRecompile || !myRecompileMarked.isEmpty() || !myDeleted.isEmpty();
  }

  @Override
  public @NotNull Iterable<@NotNull NodeSource> getDeletedSources() {
    return myDeleted;
  }

  @Override
  public @NotNull Iterable<@NotNull NodeSource> getSourcesToRecompile() {
    return myIsWholeTargetRecompile? getBaseSnapshot().getSources() : myRecompileMarked;
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
    myRecompileMarked.clear();
  }

  @Override
  public SourceSnapshot asSnapshot() {
    SourceSnapshot baseSnapshot = getBaseSnapshot();
    return !hasChanges()? baseSnapshot : new SourceSnapshot() {
      @Override
      public @NotNull Iterable<@NotNull NodeSource> getSources() {
        return flat(myDeleted, baseSnapshot.getSources());
      }

      @Override
      public @NotNull String getDigest(NodeSource src) {
        return isRecompile(src) || myDeleted.contains(src)? RECOMPILED_SOURCE_DIGEST : baseSnapshot.getDigest(src);
      }
    };
  }
}
