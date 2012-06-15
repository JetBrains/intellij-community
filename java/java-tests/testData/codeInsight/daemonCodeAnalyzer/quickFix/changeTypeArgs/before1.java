// "Change type arguments to <String>" "true"
class Generic<E> {
  Generic(E arg) {
  }
}

class Tester {
  void method() {
    new Generic<Integer>(<caret>"hi");
  }
}