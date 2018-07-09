class Test10 {
  void test() {
      newMethod();

      newMethod();
  }

    private void newMethod() {
        new Super() {
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