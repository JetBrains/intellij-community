class Test10 {
  void test() {
    <selection>new Super() {
      int get() {
        return 0;
      }
    };</selection>

        new Super() {
          @Override
          int get() {
            return 0;
          }
        };
  }
}
class Super {
  int get() {
    return 1;
  }
}