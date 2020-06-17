// "Collapse into loop" "true"
class X {
  void test() {
      for (int i : new int[]{1, 2, 3, 4}) {
          consume(() -> i);
      }
  }
  
  void consume(IntSupplier x) {}
  
  interface IntSupplier {
    int supply();
  }
}