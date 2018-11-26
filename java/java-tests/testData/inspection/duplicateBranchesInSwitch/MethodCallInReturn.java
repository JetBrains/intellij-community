enum T {
  A, B, C;

  int foo(T t) {
    switch (t) {
      case A:
        <weak_warning descr="Duplicate branch in 'switch' statement">return t.ordinal(); // comment 1</weak_warning>

      case B:
        <weak_warning descr="Duplicate branch in 'switch' statement">return t.ordinal();</weak_warning>

      case C:
        <weak_warning descr="Duplicate branch in 'switch' statement">return t.ordinal(); // comment 2</weak_warning>

      default:
        return 0;
    }
  }
}