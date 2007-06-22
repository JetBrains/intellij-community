package com.intellij.history.integration;

public interface LocalHistoryAction {
  LocalHistoryAction NULL = new LocalHistoryAction() {
    public void finish() {
    }
  };

  void finish();
}
