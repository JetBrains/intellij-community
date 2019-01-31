class MyTest {
  void m(int i) {
    String s = switch (i) {
      default -> <selection>"abc"</selection>;
    };
  }
}