class Test {
  void test(Object obj) {
    switch (obj) {
      case Object o -> {
        if (<warning descr="Condition 'o == null' is always 'false'">o == null</warning>) {}
      }
    }
    if (<warning descr="Condition 'obj == null' is always 'false'">obj == null</warning>) {}
  }

  void test2(Object obj) {
    switch (obj) {
      case null -> {}
      case Object o -> {
        if (<warning descr="Condition 'o == null' is always 'false'">o == null</warning>) {}
      }
    }
    if (obj == null) {}
  }

  void test3(Object obj) {
    switch (obj) {
      case Object o -> {
        if (<warning descr="Condition 'o == null' is always 'false'">o == null</warning>) {}
      }
      case null -> {}
    }
    if (obj == null) {}
  }
}