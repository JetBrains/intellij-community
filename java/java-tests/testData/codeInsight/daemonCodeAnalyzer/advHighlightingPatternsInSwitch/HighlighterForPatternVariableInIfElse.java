class Main {
  void test(Object o) {
    if (!(o instanceof String <caret>s && s.length() > 1)) {
      s = "fsfsdfsd"; // unresolved
    }
    else {
      s = "fsfsdfsd";
      System.out.println(s);
    }
  }
}