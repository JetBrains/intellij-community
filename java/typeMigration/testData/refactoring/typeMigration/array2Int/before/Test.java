class Test {

  int[][] x() {
    return new int[][]{{1}};
  }

  void y(int[][] i) {}

  void z() {
    y(x());
  }
}