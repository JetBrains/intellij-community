
class Bar {
  public static final String T = "";

  void m0() {
    while (T == "") {
      f();
    }
    <error descr="Unreachable statement">f();</error>
  }

  void m() {
    while (<error descr="Loop condition is always false making the loop body unreachable">T == "a"</error>) {
      f();
    }
  }

  void m01() {
    while (<error descr="Loop condition is always false making the loop body unreachable">T != ""</error>) {
      f();
    }
  }

  void m1() {
    while (T != "a") {
      f();
    }
    <error descr="Unreachable statement">f();</error>
  }

  void m2() {
    while (<error descr="Loop condition is always false making the loop body unreachable">T != T</error>) {
      f();
    }
  }

  void m3() {
    while (T == T) {}
    <error descr="Unreachable statement">f();</error>
  }

  private void f() {}
}