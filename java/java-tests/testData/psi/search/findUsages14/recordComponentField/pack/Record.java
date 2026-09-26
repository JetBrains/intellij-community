package pack;

record MyRecord (String s) {
  void foo() {
    s.substring(10, 12);
  }
}