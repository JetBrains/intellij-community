// "Change type arguments to <String>" "false"
class Generic<E> {
  Generic(E arg, int i) {
  }
}

class Tester {
  void method() {
    new Generic<Integer>(<caret>"hi");
  }
}