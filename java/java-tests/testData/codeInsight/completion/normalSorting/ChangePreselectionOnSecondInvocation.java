class Util {
  {
    Another.fooZoo<caret>
  }

}

class Another {
  private static void fooZoo() {}
  static void fooZooGoo() {}
  static void fooZooImpl() {}
}