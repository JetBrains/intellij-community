// "Insert '(String)f' declaration" "true"
class C {
  void f(Object o, Object f) {
    if (o instanceof String && f<caret> instanceof String) {
        String s = (String) o;
    }
  }
}