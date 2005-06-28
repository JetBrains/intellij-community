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


    public String toString() {
      return String.valueOf(myId);
    }

    public static Value fromString(String s){
      if (s == null) return SHOW_CONFIRMATION;
      if (s.equals("1")) return DO_NOTHING_SILENTLY;
      if (s.equals("2")) return DO_ACTION_SILENTLY;
      return SHOW_CONFIRMATION;
    }
  }

  Value getValue();
  void setValue(Value value);
}
