class C {
  void foo(String s) {
    switch (s) {
      case "A":
        bar(1);
        break;
      case "B":
        bar(2);
      case "C":
        bar(1);
    }
  }
  void bar(int n) {}
}
