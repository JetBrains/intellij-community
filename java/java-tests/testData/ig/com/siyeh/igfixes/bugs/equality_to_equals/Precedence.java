class Precedence {

  boolean m(Integer i) {
    return i++ ==<caret> Integer.valueOf(10);
  }
}