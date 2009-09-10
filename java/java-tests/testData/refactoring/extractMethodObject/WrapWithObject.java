class Test {
  void f<caret>oo(int i, int... j) {
    if (i == 0) {
        for (int idx : j) {

        }
    }
  }
}