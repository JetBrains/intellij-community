class Main {
  void foo(int i, int j) {
      ++j;
      for (; i == j;) {
          ++j;
      }
  }
}