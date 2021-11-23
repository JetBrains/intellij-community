// "Change type arguments to <String>" "true"
class Generic<E> {
  Generic(E arg, E arg1, int a) {
  }
}

class Tester {
  void method() {
    new Generic<String>("hi", "hi2", 42);
  }
}