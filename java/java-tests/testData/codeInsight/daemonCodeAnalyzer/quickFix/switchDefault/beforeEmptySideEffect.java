// "Unwrap 'switch'" "true-preview"
class X {
  int[] someArray;

  native boolean someCondition(int i);

  void test(int i) {
    do {
      switch<caret> (someArray[--i]) {
        default:
          break;
      }
    } while (someCondition(i));
  }
}