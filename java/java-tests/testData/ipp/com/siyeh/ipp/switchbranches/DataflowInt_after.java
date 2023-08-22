class DataflowInt {
  void test(int i) {
    assert i > 0 && i < 10 && i % 2 == 1;

    switch<caret> (i) {
        case 1:
            break;
        case 3:
            break;
        case 5:
            break;
        case 7:
            break;
        case 9:
            break;
    }
  }
}