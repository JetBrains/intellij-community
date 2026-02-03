// "Apply all 'Create block' fixes in file" "true"
class X {
  void test(int x) {
    switch(x) {
      case 1 -> <caret>;
      case 2 -> ;
      case 3 -> ;
    }
  }
}