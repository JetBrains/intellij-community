class Main {
  void foo1() {
    System.out.println(<warning descr="Unnecessary empty string argument">""</warning>);
  }

  void foo2() {
    StringBuilder stringBuilder = new StringBuilder(<warning descr="Unnecessary empty string argument">""</warning>);
  }

  void foo3() {
    StringBuffer stringBuffer = new StringBuffer(<warning descr="Unnecessary empty string argument">""</warning>);
  }
}
