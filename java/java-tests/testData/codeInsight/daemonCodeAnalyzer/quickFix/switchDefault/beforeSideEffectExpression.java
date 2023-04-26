// "Unwrap 'switch'" "true-preview"
class X {
  int[] someArray;

  native boolean someCondition(int i);

  void test(int i) {
    int x = <caret>switch (someArray[--i]) {
      default -> 1;
    };
  }
}