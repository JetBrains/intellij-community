class X {
  // IDEA-246054
  void test(int[] data) {
    for (int i = -1; i < data.length; i++) {
      try {
        data[i] = 0;
      } catch (ArrayIndexOutOfBoundsException ignored) {

      }
    }
  }
}