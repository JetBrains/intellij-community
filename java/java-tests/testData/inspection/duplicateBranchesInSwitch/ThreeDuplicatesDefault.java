class C {
  void foo(int n) {
    switch (n) {
      case 1:
        <weak_warning descr="Branch in 'switch' statement is a duplicate of the default branch">bar("A");</weak_warning>
        break;
      case 2:
        bar("B");
        break;
      case 3:
        <weak_warning descr="Branch in 'switch' statement is a duplicate of the default branch">bar("A");</weak_warning>
        break;
      default:
        bar("A");
        break;
    }
  }
  void bar(String s){}
}