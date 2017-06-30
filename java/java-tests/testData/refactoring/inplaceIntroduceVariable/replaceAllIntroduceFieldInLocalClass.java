class Main {
  int getSomething() {return 0;}

  void testSimple() {
    class X {
      void test() {
        int x = getSomething();
      }

      void test2() {
        int y = get<caret>Something();
      }
    }
  }
}