class C {
  void foo(int n) {
    switch (n) {
      case 1:
        bar("A");
      case 2:
        break;
      case 3:
        <weak_warning descr="Duplicate branch in 'switch' statement">bar("A");</weak_warning>
        break;
      case 4:
        <weak_warning descr="Duplicate branch in 'switch' statement">bar("A");</weak_warning>
      case 5:
    }
  }
  void bar(String s){}
}