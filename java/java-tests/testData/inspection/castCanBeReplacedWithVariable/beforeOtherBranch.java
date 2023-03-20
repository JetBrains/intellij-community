// "Replace '(String)o' with 's'" "false"

class C {
  void foo(Object o) {
    if (o instanceof String) {
      String s = (String)o;
    }
    if (o instanceof String) {
      String t = (Stri<caret>ng)o;
    }
  }
}