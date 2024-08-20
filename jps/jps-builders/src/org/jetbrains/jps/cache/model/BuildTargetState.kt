// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.cache.model;

import java.util.Objects;

public final class BuildTargetState {
  private final long hash;
  private final String relativePath;

  public BuildTargetState(long hash, String relativePath) {
    this.hash = hash;
    this.relativePath = relativePath;
  }

  public long getHash() {
    return hash;
  }

  public String getRelativePath() {
    return relativePath;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BuildTargetState state = (BuildTargetState)o;
    return hash == state.hash && Objects.equals(relativePath, state.relativePath);
  }

  @Override
  public int hashCode() {
    int result = Long.hashCode(hash);
    result = 31 * result + (relativePath != null ? relativePath.hashCode() : 0);
    return result;
  }
}