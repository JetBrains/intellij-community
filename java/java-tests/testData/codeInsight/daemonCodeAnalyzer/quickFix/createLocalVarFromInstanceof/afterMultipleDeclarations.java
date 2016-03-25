// "Insert '(String)f' declaration" "true"
class C {
  void f(Object o, Object f) {
    if (o instanceof String && f instanceof String) {
        String s = (String) o;
        String f1 = (String) f;
        <caret>
    }
  }
}