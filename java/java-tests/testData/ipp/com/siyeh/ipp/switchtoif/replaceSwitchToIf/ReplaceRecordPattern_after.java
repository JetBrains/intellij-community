import java.util.Objects;

class Test {

  void foo(Object o) {
      if (Objects.requireNonNull(o) instanceof Point(
              int x, int yy
      ) point && (x == 1 ? x + point.y() + yy == 42 : point.y() == 1)) {
          System.out.println("one");
      } else if (o instanceof Point(int x, int y) && (x == 2 && y == 3 || 1 == x)) {
          System.out.println("two");
      } else {
          System.out.println("default");
      }
  }
}

record Point(int x, int y) {
}