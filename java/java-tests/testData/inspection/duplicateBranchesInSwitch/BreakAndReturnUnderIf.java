class C {
  int foo(int n, boolean b) {
    switch (n) {
      case 1:
        if(b) {
          return bar("A");
        } else {
          break;
        }
      case 2:
        if(b) {
          return bar("B");
        } else {
          break;
        }
      case 3:
        <weak_warning descr="Duplicate branch in 'switch' statement">if(b) {
          return bar("A");
        } else {
          break;
        }</weak_warning>
    }
    return 0;
  }
  int bar(String s){return s.charAt(0);}
}