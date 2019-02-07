class C {
  void foo(int n) {
    switch (n) {
      case 1:
        bar("A");
      case 2:
        bar("B");
        break;
      case 3:
        bar("A");
        break;
    }
  }
  void bar(String s){}
}