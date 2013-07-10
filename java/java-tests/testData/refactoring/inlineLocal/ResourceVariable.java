class Test {
  void m() throws Exception {
    try (AutoCloseable inl<caret>ineMe = null) {
      try (AutoCloseable r2 = inlineMe) {
        System.out.println(inlineMe + ", " + r2);
      }
    }
  }
}