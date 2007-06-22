package com.intellij.history;

public interface LocalHistoryAction {
  LocalHistoryAction NULL = new LocalHistoryAction() {
    public void finish() {
    }
  };

  void finish();
}
