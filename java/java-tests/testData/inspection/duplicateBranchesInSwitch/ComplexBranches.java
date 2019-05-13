class C {
  void foo(int n, boolean b) {
    switch (n) {
      case 1:
        if(b) {
          bar("A");
        } else {
          bar("z");
        }
        bar("o");
        break;
      case 2:
        if(b) {
          bar("B");
        } else {
          bar("z");
        }
        bar("o");
        break;
      case 3:
        <weak_warning descr="Duplicate branch in 'switch' statement">if(b) {
          bar("A");
        } else {
          bar("z");
        }
        bar("o");</weak_warning>
        break;
    }
  }
  void bar(String s){}
}