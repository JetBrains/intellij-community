class Main {
  void foo1(String str) {
    str.lastIndexOf("42", <warning descr="Unnecessary string length argument">str.length()</warning>);
  }

  void foo2(String str) {
    str.lastIndexOf("42", <warning descr="Unnecessary string length argument">str.length() - 1</warning>);
  }
}
