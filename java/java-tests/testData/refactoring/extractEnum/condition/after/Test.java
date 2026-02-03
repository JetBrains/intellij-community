class Test {
  private final DebuggerSessionState myState;

    public Test() {
    myState = new DebuggerSessionState(EEnum.STATE_STOPPED);
  }

  EEnum getState() {
    return myState.myState;
  }
  
  void setState(EEnum i) {
  }
  
  void b(Test session) {
    setState(session != null ? session.getState() : EEnum.STATE_STARTED);
  }
  
  {
    if (EEnum.STATE_STARTED == getState()) {
      System.out.println();
    }
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