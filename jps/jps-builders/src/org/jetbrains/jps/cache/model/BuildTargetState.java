package org.jetbrains.jps.cache.model;

import java.util.Objects;

public final class BuildTargetState {
  private final String hash;
  private final String relativePath;

  private BuildTargetState(String hash, String relativePath) {
    this.hash = hash;
    this.relativePath = relativePath;
  }

  public String getHash() {
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
    if (!Objects.equals(hash, state.hash)) return false;
    if (!Objects.equals(relativePath, state.relativePath)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = hash != null ? hash.hashCode() : 0;
    result = 31 * result + (relativePath != null ? relativePath.hashCode() : 0);
    return result;
  }
}