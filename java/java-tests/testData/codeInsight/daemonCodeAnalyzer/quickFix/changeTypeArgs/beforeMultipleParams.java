// "Change type arguments to <String, Integer>" "true"
class Generic<E, K> {
  Generic(E arg, K arg1) {
  }
}

class Tester {
  void method() {
    new Generic<Integer, Integer>(<caret>"hi", 1);
  }
}