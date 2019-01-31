class C {
  void foo(int n) {
    switch (n) {
      case 1 -> bar("A");
      case 2 -> bar("B");
      case 3 -> <weak_warning descr="Duplicate branch in 'switch' statement">bar("A");</weak_warning>
    }
  }
  void bar(String s){}
}