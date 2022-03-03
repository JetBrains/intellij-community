// "Change type arguments to <String>" "true"
class Generic<E> {
  Generic(E arg, E arg1, int a) {
  }
}

class Tester {
  void method() {
    new Generic<Integer>("hi", <caret>"hi2", 42);
  }
}