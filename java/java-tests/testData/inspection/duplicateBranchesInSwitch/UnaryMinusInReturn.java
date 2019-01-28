enum T {
  A, B, C;

  int foo(T t) {
    switch (t) {
      case A:
        return -1; // comment 1

      case B:
        return 1;

      case C:
        return -1; // comment 2

      default:
        return 0;
    }
  }
}