class Test{

  public void t() {
    Object <warning descr="Variable 'v' can have 'final' modifier">v</warning>;
    if (true) {
      v = new Object(); 
    } else {
      v = new Object();
    }
  }
}