package com.intellij.openapi.vcs;

public interface VcsShowConfirmationOption {
  enum Value {
    SHOW_CONFIRMATION(0),
    DO_NOTHING_SILENTLY(1),
    DO_ACTION_SILENTLY(2);

    private final int myId;

    Value(final int id) {
      myId = id;
    }
  }

  Value getValue();
  void setValue(Value value);
}
