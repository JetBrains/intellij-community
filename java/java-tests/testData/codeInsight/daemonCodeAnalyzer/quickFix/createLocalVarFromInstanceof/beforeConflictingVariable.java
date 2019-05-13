// "Insert '(String)o' declaration" "true"
class C {
  void f(Object o, Object f) {
    if (<caret>o instanceof String) {
        Float s = 1f;
    }
  }
}