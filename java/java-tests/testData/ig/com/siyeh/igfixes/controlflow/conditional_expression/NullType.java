class ConvertToIf {

  void test(boolean flag) {
    (flag <caret>? null : null).hashCode();
  }
}
