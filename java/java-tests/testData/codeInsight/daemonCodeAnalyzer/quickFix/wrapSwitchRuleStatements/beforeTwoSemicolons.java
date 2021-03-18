// "Wrap with block" "true"
class X {
  void test(int x) {
    switch(x) {
      case 1 -> <caret>;  /**/  ;
    }
  }
}