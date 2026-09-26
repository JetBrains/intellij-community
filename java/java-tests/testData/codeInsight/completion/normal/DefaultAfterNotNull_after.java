class Main {
  enum Numbers{ONE, TWO}

  private static void testEnum2(Object r2) {
    switch (r2) {
      case Numbers.ONE, defau<caret>:
        break;
    }
  }
}
