class C {
  void foo(int n) {
    switch (n) {
      case 2:
        bar("B");
        break;
      default:
        bar("A");
        break;
      case 1:
        <weak_warning descr="Branch in 'switch' statement is a duplicate of the default branch">bar("A");</weak_warning>
        break;
    }
  }
  void bar(String s){}
}