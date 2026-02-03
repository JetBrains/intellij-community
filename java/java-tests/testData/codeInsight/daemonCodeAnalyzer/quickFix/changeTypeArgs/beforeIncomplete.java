// "Change type arguments to <String>" "false"
class Generic<E> {
  Generic(E arg) {
  }
}

class Tester {
  void method() {
    new Generic<Integer>(<caret>"hi"
  }
}