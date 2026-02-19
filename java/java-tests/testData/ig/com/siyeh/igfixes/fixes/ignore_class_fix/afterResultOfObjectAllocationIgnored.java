// "Ignore allocations of objects with type 'java.lang.String'" "true"

class X {
  void x() {
    new <caret>String();
  }
}