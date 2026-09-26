class Main {
  record Point(int x, int y) {
  }

  record Rect(Point point1, Point point2) {
  }

  void test1(final Object obj) {
    if (obj instanceof Point(int <warning descr="Parameter 'x' can have 'final' modifier">x</warning>, int y)) {
      System.out.println(x);
      y = 42;
    }
  }

  void test2(final Object obj) {
    if (obj instanceof Rect(Point(int <warning descr="Parameter 'x1' can have 'final' modifier">x1</warning>, int y1), Point point2) && (y1 = 42) == x1 && (point2 = null) == null) {
      System.out.println(x1);
    }
  }

  void test3(final Object obj) {
    switch (obj) {
      case Rect(Point(int <warning descr="Parameter 'x1' can have 'final' modifier">x1</warning>, int y1), Point point2) when (<error descr="Cannot assign a value to variable 'y1', because it is declared outside the guard">y1</error> = 42) == x1 -> {
        point2 = new Point(0, 0);
      }
      default -> {}
    }
  }

  void test4(final Object obj) {
    if (obj instanceof Point(int <warning descr="Parameter 'x' can have 'final' modifier">x</warning>, int y) && (y = 42) == x) {
      System.out.println(x);
    }
  }

  void test5(final Point[] points) {
    for (<error descr="Record patterns in for-each loops are not supported at language level '21'">Point(int <warning descr="Variable 'x' can have 'final' modifier">x</warning>, int y)</error> : points) {
      System.out.println(x);
      y = 42;
    }
  }

  void test6(final Rect[] rects) {
    for (<error descr="Record patterns in for-each loops are not supported at language level '21'">Rect(Point(int x1, int <warning descr="Variable 'y1' can have 'final' modifier">y1</warning>), Point <warning descr="Variable 'point2' can have 'final' modifier">point2</warning>)</error> : rects) {
      x1 = 42;
    }
  }

  void test7(final Object obj) {
    switch (obj) {
      case Point point:
        point = new Point(0, 0);
        break;
      case Rect <warning descr="Parameter 'rect' can have 'final' modifier">rect</warning>:
        System.out.println("rectangle");
        break;
      default:
        System.out.println("default");
    }
  }

  void test8(final Object obj) {
    final I i = new I() {
      void foo() {
        if (obj instanceof Point(int <warning descr="Parameter 'x' can have 'final' modifier">x</warning>, int <warning descr="Parameter 'y' can have 'final' modifier">y</warning>)) {}
      }
    };
  }

  interface I {}
}

