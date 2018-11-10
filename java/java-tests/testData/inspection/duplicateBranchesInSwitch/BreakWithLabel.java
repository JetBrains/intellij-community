class C {
  void foo(int n) {
    OuterLabel:
    if (n > 0) {
      switch (n) {
        case 1:
         bar("A");
          break;
        case 2:
          bar("B");
          break;
        case 3:
          bar("A");
          break OuterLabel;
      }
      bar("Z");
    }
  }
  void bar(String s){}
}