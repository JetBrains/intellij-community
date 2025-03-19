class Test {
  void test(Object obj) {
    switch (obj) {
      case String s:
        int <caret>x = 10;
    }
  }
}
