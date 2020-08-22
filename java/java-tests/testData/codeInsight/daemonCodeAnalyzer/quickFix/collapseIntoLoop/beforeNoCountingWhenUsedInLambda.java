// "Collapse into loop" "true"
class X {
  void test() {
    <caret>consume(() -> 1);
    consume(() -> 2);
    consume(() -> 3);
    consume(() -> 4);
  }
  
  void consume(IntSupplier x) {}
  
  interface IntSupplier {
    int supply();
  }
}