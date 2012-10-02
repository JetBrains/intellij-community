class Test {
  {
      bar(<selection>Test::length</selection>);
  }

  public static Integer length(String s) {
    return s.length();
  }

    static void bar(Bar bar) {}
  interface Bar {
    Integer _(String s);
  }
}