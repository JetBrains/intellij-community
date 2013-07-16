class Test {
  private void test() {
    int state = 1;
    switch (<warning descr="Value 'state' is always '1'">state</warning>) {
      case 1: break;
    }
  }

}