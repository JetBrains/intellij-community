class Test {
  String str;

  void foo(String[] p) {
    for (String number : p) {
      number = str;
    }
  }
}