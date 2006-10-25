package com.intellij.localvcs;

public interface Change {
  void applyTo(Snapshot snapshot);

  void revertOn(Snapshot snapshot);
}
