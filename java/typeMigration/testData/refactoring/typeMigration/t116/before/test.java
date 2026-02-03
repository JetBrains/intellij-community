class Test {
  Number str;

  void foo(String[] p) {
    for (Number number : p) {
      number = str;
    }
  }
}