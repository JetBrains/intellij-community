class Test {
  {
    ((Bar) Test::length).m("");
  }

  public static Integer length(String s) {
    return s.length();
  }

  interface Bar {
    Integer m(String s);
  }
}