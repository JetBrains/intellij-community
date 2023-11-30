class C {

  void x(int a, int b) {
      //keep me
      String s = java.text.MessageFormat.format("""
              the text\s
               block
              \\line2
              {0}{1} "to" be""", a, b);
  }
}