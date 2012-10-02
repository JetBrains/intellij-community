class Test {
  {
      Bar bar = Test::length;
      b<caret>ar._("");
  }

  public static Integer length(String s) {
    return s.length();
  }

  interface Bar {
    Integer _(String s);
  }
}