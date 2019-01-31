class C {
  void foo(int n) {
    switch (n) {
      case 1:
      case 2:
        bar("A");
        break;
      case 3:
        <weak_warning descr="Duplicate branch in 'switch' statement">bar("A");</weak_warning>
        break;
      case 4:
        bar("B");
        break;
    }
  }
  void bar(String s){}
}