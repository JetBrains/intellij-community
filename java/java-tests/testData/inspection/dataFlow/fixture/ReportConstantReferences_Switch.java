class Test {
  private void test() {
    int state = 1;
    switch (<warning descr="Value 'state' is always '1'">state</warning>) {
      case 1: break;
    }
  }

  private void test2(int state) {
    switch (state) {
      case ONE:
      case TWO:
        if (state == TWO) {
          System.out.println("hello");
        }
    }
  }

  public static final int ONE = 1;
  public static final int TWO = 2;

}