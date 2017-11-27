class Test{

  public void t() {
    Object v;
    Object <warning descr="Variable 'o' can have 'final' modifier">o</warning>;
    if (true) {
      v = new Object(); 
      o = new Object();
    } else {
    }
    System.out.println(v);
  }
}