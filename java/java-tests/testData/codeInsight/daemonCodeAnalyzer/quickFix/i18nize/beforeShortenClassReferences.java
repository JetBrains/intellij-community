package p;
class MyBundle {
  static String message(String key) {
    return key;
  }
}
class a {
    void f() {
      String s = "x<caret>xxxx";
    }
}