// "Convert to atomic" "true"
class A {
  boolean bo<caret>ol = false;
  void testAtomicBool() {
    bool |= Math.random() > 0.5;
  }
}