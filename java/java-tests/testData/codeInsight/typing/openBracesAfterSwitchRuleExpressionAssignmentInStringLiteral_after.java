class X {
  public static String foo(int bar) {
    String path1 = null;
    switch (foo) {
      case 1 -> path1 = "bar{<caret>";
      case 2 -> path1 = "Hello";
    }
    return path1;
  }
}
