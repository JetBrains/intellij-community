class Main {
  int test1(int i) {
    return switch ((<warning descr="Casting 'i' to 'Integer' is redundant">Integer</warning>)i) {
      default -> 42;
    };
  }

  void test2(int i) {
    switch ((<warning descr="Casting 'i' to 'Integer' is redundant">Integer</warning>)i) {
    }
  }

  void test3(int i) {
    switch ((Integer)i) {
      case Integer integer -> {}
    }
  }

  int test4(int i) {
    return switch ((Integer)i) {
      case 0 -> 0;
      case <error descr="Guarded patterns from JEP 406 are not available since Java 19 preview">Integer integer && Math.random() > 0.5</error> -> 7;
      default -> 42;
    };
  }

  int test5(int i) {
    return switch ((Integer)i) {
      case 0 -> 0;
      case null, default -> 42;
    };
  }
}