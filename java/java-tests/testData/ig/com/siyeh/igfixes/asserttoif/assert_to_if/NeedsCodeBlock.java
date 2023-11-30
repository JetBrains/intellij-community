class NeedsCodeBlock {

  void x() {
    if (false) assert<caret> false : "false";
    else {
      System.out.println("hello");
    }
  }
}