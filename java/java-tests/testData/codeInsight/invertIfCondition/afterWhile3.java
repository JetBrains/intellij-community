// "Invert 'if' condition" "true"
class A {
  void m(boolean b) {
    while (true) {
        if (!b) {
            break;
        }
    }
  }
}