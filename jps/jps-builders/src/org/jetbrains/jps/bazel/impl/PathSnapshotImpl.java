// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.bazel.PathSnapshot;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

public class PathSnapshotImpl implements PathSnapshot {
  private final Map<Path, String> myPaths;

  public PathSnapshotImpl(Map<Path, String> digestPaths) {
    myPaths = Collections.unmodifiableMap(digestPaths); // keep all properties of the passed map, i.e. iteration order
  }

  @Override
  public @NotNull Iterable<@NotNull Path> getElements() {
    return myPaths.keySet();
  }

  @Override
  public @NotNull String getDigest(Path src) {
    return myPaths.getOrDefault(src, "");
  }
}
