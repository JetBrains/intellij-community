// "Change 'new Y()' to 'new Y()'" "false"

class X {
  Y x() {
    class Y {}
    return new <caret>Y();
  }
}
