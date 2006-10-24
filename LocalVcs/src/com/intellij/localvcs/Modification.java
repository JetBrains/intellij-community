package com.intellij.localvcs;

public interface Modification {
  void applyTo(Snapshot snapshot);

  void revertOn(Snapshot snapshot);
}
