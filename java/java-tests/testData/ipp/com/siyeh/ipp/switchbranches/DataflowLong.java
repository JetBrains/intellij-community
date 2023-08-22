class DataflowLong {
  void test() {
    long x = Math.random() > 0.5 ? 1 : 2;

    switch<caret> (x) {

    }
  }
}