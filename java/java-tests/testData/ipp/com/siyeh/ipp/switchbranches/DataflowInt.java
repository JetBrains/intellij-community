class DataflowInt {
  void test(int i) {
    assert i > 0 && i < 10 && i % 2 == 1;

    switch<caret> (i) {

    }
  }
}