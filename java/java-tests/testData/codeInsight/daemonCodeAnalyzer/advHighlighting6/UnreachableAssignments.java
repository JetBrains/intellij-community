class Test {
  String[] days = {"",""};

  public static void foo1() {
    for (; ; ) {
      break;
      <error descr="Unreachable statement">new Test().days = null;</error>
    }
  }
  
  void bar1() {
    for (; ; ) {
      break;
      <error descr="Unreachable statement">days = null;</error>
    }
  }

  static void foo2() {
    for (; ; ) {
      break;
      <error descr="Unreachable statement">new Test().days[0] = null;</error>
    }
  }

  void bar2() {
    for (;;) {
      break;
      <error descr="Unreachable statement">days[0] = null;</error>
    }
  }
}