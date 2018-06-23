class Main {
  public static void main(String[] args) {
    lbl:
    while (true) {
      int i = 0;
      do {
        if (i++ > 7) br<caret>eak;
        if (i % 2 == 0) continue;
        if (--i > 8) continue lbl;
      } while (args != null);
    }
  }
}