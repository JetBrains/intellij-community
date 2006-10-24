package com.intellij.localvcs;

public interface Modification {
  public abstract void applyTo(Snapshot snapshot);
}
