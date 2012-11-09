class Test {

    static class Inner {
          public static void get() {
          }
      }
    Inner p;

    {
        Inner.get();
    }
}
