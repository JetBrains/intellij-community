enum T {
  A, B, C;

  int foo(T t) {
    switch (t) {
      case A:
        return t.ordinal(); // comment 1

      case B:
        <info descr="Duplicate branch in 'switch'">return t.ordinal();</info>

      case C:
        <info descr="Duplicate branch in 'switch'">return t.ordinal(); // comment 2</info>

      default:
        return 0;
    }
  }
}