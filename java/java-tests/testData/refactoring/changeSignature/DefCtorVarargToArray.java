class X {
  <caret>X(String... args) {}
  
  X(int x) {
    this();
  }
}
class Y extends X {}