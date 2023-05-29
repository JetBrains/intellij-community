class StringThatNeedsEscaping {

  void x(String <caret>s) {
    System.out.println(s);
  }

  void y() {
    x("quote" + "\"");
  }
}