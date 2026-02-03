class MainTest {
  static int getIndex() {
    if (Math.random() > 0.7) {
      return 1;
    }
    if (Math.random() > 0.7) {
      return 2;
    }
    return <flown1111>-1;
  }

  static void test(int[] data, int <flown1>src, int dst, int len) { System.arraycopy(data, <caret>src, data, dst, len); }

  public static void main(String[] args) {
    int[] data = new int[10];
    int index = <flown111>getIndex();
    assert index >= 0;
    test(data, <flown11>index, getIndex(), getIndex());
  }
}