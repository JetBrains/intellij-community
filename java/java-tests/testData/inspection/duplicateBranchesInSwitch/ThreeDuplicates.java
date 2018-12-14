class C {
  void foo(int n) {
    switch (n) {
      case 1:
        bar("A");
        break;
      case 2:
        bar("B");
        break;
      case 3:
        <weak_warning descr="Duplicate branch in 'switch' statement">bar("A");</weak_warning>
        break;
      case 4:
        <weak_warning descr="Duplicate branch in 'switch' statement">bar("A");</weak_warning>
        break;
    }
  }
  void bar(String s){}
}