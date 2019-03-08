// "Insert '(String)x' declaration" "true"

class C {
  Object x = new Object();

  void x() {
    if (x instanceof String) {
        String x = (String) this.x;
        <caret>
    }
  }
}