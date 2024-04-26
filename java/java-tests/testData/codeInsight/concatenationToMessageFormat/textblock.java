class C {

  void x(int a, int b) {
    String s = """
      the text \n block
      \\line2
      """ +
               a + b + <caret>//keep me
               " \"to\" be";
  }
}