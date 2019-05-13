class Test {
  private final DebuggerSessionState myState;

  public static final int STATE_STARTED = 0;
  public static final int STATE_STOPPED = 1;

  public Test() {
    myState = new DebuggerSessionState(STATE_STOPPED);
  }

  int getState() {
    return myState.myState;
  }
  
  void setState(int i) {
  }
  
  void b(Test session) {
    setState(session != null ? session.getState() : STATE_STARTED);
  }
  
  {
    if (STATE_STARTED == getState()) {
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
    final int myState;
    
    public DebuggerSessionState(int state) {
      myState = state;
    }
  }
}