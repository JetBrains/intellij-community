// "Replace 'if else' with '||'" "true"
class Comments {

  void m(boolean b) {
    boolean c = b || f();
      // 1
      // 3
      // 4
      //5
      // 2
      //6
  }

  boolean f() {
    return true;
  }
}