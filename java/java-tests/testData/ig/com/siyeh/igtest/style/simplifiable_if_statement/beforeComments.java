// "Replace 'if else' with '||'" "true"
class Comments {

  void m(boolean b) {
    boolean c;
    if<caret> (b)  // 1
      c = true;                                         // 2
    else { // 3
      c = f(); // 4
    } //5
    //6
  }

  boolean f() {
    return true;
  }
}