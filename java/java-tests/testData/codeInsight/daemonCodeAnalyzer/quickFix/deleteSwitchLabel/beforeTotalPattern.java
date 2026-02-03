// "Remove switch branch 'Integer ii when true'" "true-preview"
class Test {
  Integer i = 1;
  void test() {
    switch (i) {
      case <caret>Integer ii when true:
        break;
      case Object o:
        break;
    }
  }
}