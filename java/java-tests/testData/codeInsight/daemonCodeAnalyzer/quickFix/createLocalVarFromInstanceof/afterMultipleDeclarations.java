// "Insert '(String)f' declaration" "true-preview"
class C {
  void f(Object o, Object f) {
    if (o instanceof String && f instanceof String) {
        String s = (String) o;
        String string = (String) f;
        <caret>
    }
  }
}