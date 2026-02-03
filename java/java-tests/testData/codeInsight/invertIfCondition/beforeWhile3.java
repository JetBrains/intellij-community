// "Invert 'if' condition" "true"
class A {
  void m(boolean b) {
    while (true) {
      <caret>if (b) {
        continue;
      }
      break;
    }
  }
}