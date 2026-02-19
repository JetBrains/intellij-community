class Main {
  int test1(int i) {
    return switch ((<warning descr="Casting 'i' to 'Integer' is redundant">Integer</warning>)i) {
      default -> 42;
    };
  }

  void test2(int i) {
    switch ((<warning descr="Casting 'i' to 'Integer' is redundant">Integer</warning>)i) {
      default -> {}
    };
  }

  int test3(int i) {
    return switch ((Integer)i) {
      case Integer integer -> 42;
    };
  }

  int test4(int i) {
    return switch ((Integer)i) {
      case 0 -> 0;
      case Integer integer when Math.random() > 0.5 -> 7;
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