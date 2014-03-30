// "Change type arguments to <Serializable & Comparable<? extends Serializable & Comparable<? extends Comparable<?>>>>" "true"
class Generic<E> {
  Generic(E arg, E arg1) {
  }
}

class Tester {
  void method() {
    new Generic<Integer>(<caret>"hi", 1);
  }
}