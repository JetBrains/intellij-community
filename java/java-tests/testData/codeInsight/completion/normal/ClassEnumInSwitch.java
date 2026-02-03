class Main {
  enum Numbers{ONE, TWO}

  private static void testEnum2(Numbers r2) {
    switch (r2) {
      case Numbers.ONE:
        break;
      case TWO:
        System.out.println("2");
        break;
      case Numb<caret>
    }
  }
}
