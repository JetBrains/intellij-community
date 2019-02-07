class C {
  void foo(int n) {
    switch (n) {
      case 1:
        bar("A");
      case 2:
        bar("A");
      case 3:
        bar("A");
    }
  }
  void bar(String s){}
}