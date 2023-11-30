class C {
    String s = """
      the text \n block
      \\line2
    """ +
    1 + 2 +<caret>//keep me
      " to be";
}