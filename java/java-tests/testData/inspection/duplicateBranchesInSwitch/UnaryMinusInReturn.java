enum T {
  A, B, C;

  int foo(T t) {
    switch (t) {
      case A:
        return -1; // comment 1

      case B:
        return 1;

      case C:
        <info descr="Duplicate branch in 'switch'">return -1; // comment 2</info>

      default:
        return 0;
    }
  }
}