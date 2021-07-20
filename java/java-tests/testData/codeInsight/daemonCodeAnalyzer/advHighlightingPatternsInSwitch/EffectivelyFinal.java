class Test {
  void test1(Object o, int mode) {
    switch (o) {
      case (Integer i) && i == <error descr="Variable used in guarded pattern should be final or effectively final">mode</error> -> System.out.println();
      default -> {}
    }

    switch (o) {
      case (Integer i) && (switch (o) {
        case Integer ii && ii != <error descr="Variable used in guarded pattern should be final or effectively final">mode</error> -> 2;
        case default -> 1;
      }) == <error descr="Variable used in guarded pattern should be final or effectively final">mode</error> -> System.out.println();
      default -> {}
    }

    switch (o) {
      case (Integer i) && (i = <error descr="Variable used in guarded pattern should be final or effectively final">mode</error>) > 0 -> System.out.println();
      default -> {}
    }
    mode = 0;
  }

  void test2(Object o, final int mode) {
    switch (o) {
      case (Integer i) && (switch (<error descr="Variable used in guarded pattern should be final or effectively final">o</error>) {
        case Integer ii && ii != mode -> 2;
          case default -> 1;
      }) == mode -> o = null;
        default -> {}
    }
    switch (o) {
      case (Integer i) && (i = mode) > 0 -> System.out.println();
      default -> {}
    }
  }

  void test3(Object o, int mode) {
    switch (o) {
      case (Integer i) && i == mode -> System.out.println();
      default -> {}
    }
    switch (o) {
      case (Integer i) && (switch (o) {
        case Integer ii && ii != mode -> 2;
        case default -> 1;
      }) == mode -> System.out.println();
      default -> {}
    }
  }

  void testInstanceofPatterns(Object o, int mode) {
    if (o instanceof Integer i && (i = mode) > 0) {
    }
    mode = 0;
  }
}