class Test {
  {
    ((Bar) Test::length).m("");
  }

  public static Integer length(String s) {
    return s.length();
  }

  void test() {
    synchronized ((Bar)Test::length) {
      System.out.println("Synchronizing on a method reference is a nice thing to try!");
    }
  }

  interface Bar {
    Integer m(String s);
  }
}