
class Test {
  static final int[] DATA = {1, 2, 3};

  int test(int value) {
    try {
      return DATA[value];
    }
    catch (ArrayIndexOutOfBoundsException e) {
      return -1;
    }
  }

  void test2(int value) {
    try {
      DATA[value] = -1;
    }
    catch (ArrayIndexOutOfBoundsException e) {
      System.out.println(e);
    }
  }
}