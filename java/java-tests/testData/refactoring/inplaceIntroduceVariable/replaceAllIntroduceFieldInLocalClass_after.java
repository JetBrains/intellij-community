class Main {
  int getSomething() {return 0;}

  void testSimple() {
    class X {
        private final int smth = getSomething();

        void test() {
        int x = smth;
      }

      void test2() {
        int y = smth;
      }
    }
  }
}