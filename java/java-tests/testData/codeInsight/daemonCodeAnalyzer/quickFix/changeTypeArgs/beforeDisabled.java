// "Change type arguments to <String>" "false"
import java.util.List;

class Generic<E> {
  Generic(E arg, List<E> arg1) {
  }
}

class Tester {
  void method() {
    new Generic<Integer>(<caret>"hi", "hi");
  }
}