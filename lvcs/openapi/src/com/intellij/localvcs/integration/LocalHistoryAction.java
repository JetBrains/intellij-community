package com.intellij.localvcs.integration;

public interface LocalHistoryAction {
  LocalHistoryAction NULL = new LocalHistoryAction() {
    public void finish() {
    }
  };

  void finish();
}
