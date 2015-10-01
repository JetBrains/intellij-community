class Test {
  private final DebuggerSessionState myState;

    public Test() {
    myState = new DebuggerSessionState(EEnum.STATE_STOPPED);
  }

  void a() {
    switch (myState.myState) {
      case STATE_STARTED:
        break;
      case STATE_STOPPED:
        break;
    }
  }

  private static class DebuggerSessionState {
    final EEnum myState;
    
    public DebuggerSessionState(EEnum state) {
      myState = state;
    }
  }
}