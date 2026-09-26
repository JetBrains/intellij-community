 class Super {
  protected final Object myBar;
  protected final Object myBizz;

  protected Super() {
    myBar = new Object() {
    };
    myBizz = null;
  }
}
