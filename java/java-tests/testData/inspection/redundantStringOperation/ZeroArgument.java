class Main {
  void foo1(String str) {
    str.indexOf("42", <warning descr="Unnecessary zero argument">0</warning>);
  }

  void foo2(String str) {
    str.startsWith("42", <warning descr="Unnecessary zero argument">0</warning>);
  }
}
