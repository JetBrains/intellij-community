class Test {
  {
      Bar c = Test::length;
      bar(c);
  }

  public static Integer length(String s) {
    return s.length();
  }

    static void bar(Bar bar) {}
  interface Bar {
    Integer _(String s);
  }
}