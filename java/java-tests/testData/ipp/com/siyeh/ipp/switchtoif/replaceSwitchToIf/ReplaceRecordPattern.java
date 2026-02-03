class Test {

  void foo(Object o) {
    <caret>switch (o) {
      case Point(int x, int yy) point when x == 1 ? x + point.y() + yy == 42 : point.y() == 1 -> System.out.println("one");
      case Point(int x, int y) when x == 2 && y == 3 || 1 == x -> System.out.println("two");
        case default -> System.out.println("default");
    }
  }
}

record Point(int x, int y) {
}