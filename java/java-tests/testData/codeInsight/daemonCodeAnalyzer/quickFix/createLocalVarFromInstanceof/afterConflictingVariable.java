// "Insert '(String)o' declaration" "true-preview"
class C {
  void f(Object o, Object f) {
    if (o instanceof String) {
        Float s = 1f;
        String o1 = (String) o;
        <caret>
    }
  }
}