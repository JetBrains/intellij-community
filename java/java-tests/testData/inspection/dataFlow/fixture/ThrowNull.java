class DataFlowBug {

  public void add2() {
    try {
      throw null;
    } catch (Throwable e) {
      if (e instanceof NullPointerException) {
        return;
      }
    }
  }

}