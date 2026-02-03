class NeedsCodeBlock {

  void x() {
    if (false) {
        if (true) throw new AssertionError("false");
    } else {
      System.out.println("hello");
    }
  }
}