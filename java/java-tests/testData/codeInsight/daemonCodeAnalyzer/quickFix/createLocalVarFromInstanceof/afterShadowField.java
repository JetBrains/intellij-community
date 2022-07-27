// "Insert '(String)x' declaration" "true-preview"

class C {
  Object s = new Object();

  void x() {
    if (x instanceof String) {
        String s1 = (String) x;
        <caret>
    }
  }
}