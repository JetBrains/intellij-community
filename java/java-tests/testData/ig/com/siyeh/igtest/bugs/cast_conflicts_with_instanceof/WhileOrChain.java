class WhileOrChain {
  void test(Object obj) {
    while (obj instanceof String || obj instanceof Number) {
      if (obj instanceof String || ((Number)obj).intValue() == 0) {
        obj = update();
      } else break;
    }
  }

  native Object update();
}