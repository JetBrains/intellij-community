class DataFlowBug {

  public void add2() {
    try {
      throw <warning descr="Dereference of 'null' will produce 'NullPointerException'">null</warning>;
    } catch (Throwable e) {
      if (e instanceof NullPointerException) {
        return;
      }
    }
  }

}