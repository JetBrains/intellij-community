class Test10 {
  void test() {
      newMethod();

      newMethod();
  }

    private void newMethod() {
        new Object() {
            int get() {
                return 0;
            }
        };
    }
}