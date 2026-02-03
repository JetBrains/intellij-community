// "Wrap with block" "true-preview"
class X {
  void test(int x) {
    switch(x) {
      case 1 -> <caret>;  /**/  ;
    }
  }
}