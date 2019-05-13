// "Cast to '?'" "false"
class Scope<T> {
  T val;
  void f(Scope<?> s) {
    s.val = <caret>"";
  }
}