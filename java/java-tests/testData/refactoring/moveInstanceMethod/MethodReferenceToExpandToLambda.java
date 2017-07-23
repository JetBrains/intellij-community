
class Bar {
  void f<caret>oo(int i, Bar1 b1) {
  }

}

class Bar1 {

  void m(Bar b) {
    Bar3 r = b::foo;
  }
}

interface Bar3 {
  void m(int i, Bar1 v);
}