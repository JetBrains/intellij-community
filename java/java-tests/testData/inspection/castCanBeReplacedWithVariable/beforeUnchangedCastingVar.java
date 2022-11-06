// "Replace '(String) o' with 's1'" "true-preview"

class C {
  void foo(Object o) {
    String s1 = (String) o;
    if (Math.random() > 0.5) {
      o = null;
      return;
    }
    String s2 = (String) o<caret>;
  }
}