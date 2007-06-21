package com.intellij.localvcs.integration;

public interface LocalHistoryAction {
  LocalHistoryAction NULL = new Null();

  void finish();

  class Null implements LocalHistoryAction {
    public void finish() {
    }
  }
}
