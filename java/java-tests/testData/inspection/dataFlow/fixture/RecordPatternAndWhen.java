class Test {
  void foo(Object obj) {
    if (!(obj instanceof Point)) return;
    switch (obj) {
      case <warning descr="Switch label 'Point(int x, int y)' is the only reachable in the whole switch">Point(int x, int y)</warning> -> System.out.println(1);
      default -> System.out.println(2);
    }
    switch (obj) {
      case Point(int x, int y) when x > 0 -> System.out.println(1);
      case Point(int x, int y) when <warning descr="Condition 'x <= 0' is always 'true'">x <= 0</warning> -> System.out.println(1);
      default -> System.out.println(2);
    }
  }
}

record Point(int x, int y) {
}