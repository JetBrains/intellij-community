enum E {
  A, B, C;

  int x(E e, String s) {
    return switch (<error descr="Cannot resolve symbol 'd'">d</error>) {
      case A -> 1;
      case B -> 2;
      case C -> 3;
    };
  }
}