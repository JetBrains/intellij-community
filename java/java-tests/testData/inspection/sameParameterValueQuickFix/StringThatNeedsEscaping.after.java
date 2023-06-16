class StringThatNeedsEscaping {

  void x() {
    System.out.println("quote\"");
  }

  void y() {
    x();
  }
}