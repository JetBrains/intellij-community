
class Bar {
  public static final String T = "";

  void m0() {
    while (T == "") {
      f();
    }
    <error descr="Unreachable statement">f();</error>
  }

  void m() {
    while (T == "a") <error descr="Unreachable statement">{
      f();
    }</error>
  }

  void m01() {
    while (T != "") <error descr="Unreachable statement">{
      f();
    }</error>
  }

  void m1() {
    while (T != "a") {
      f();
    }
    <error descr="Unreachable statement">f();</error>
  }

  void m2() {
    while (T != T) <error descr="Unreachable statement">{
      f();
    }</error>
  }

  void m3() {
    while (T == T) {}
    <error descr="Unreachable statement">f();</error>
  }

  private void f() {}
}