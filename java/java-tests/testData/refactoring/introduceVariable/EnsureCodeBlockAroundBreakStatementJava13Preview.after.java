class MyTest {
  void m(int i) {
    String s = switch (i) {
      default -> {
          String temp = "abc";
          yield temp;
      }
    };
  }
}