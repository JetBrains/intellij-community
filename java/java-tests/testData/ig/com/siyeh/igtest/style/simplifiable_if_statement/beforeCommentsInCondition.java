// "Replace 'if else' with '?:'" "INFORMATION"
class X {
  void x() {
    <caret>if (1 //comment
        == 2) {
      System.out.println("true");
    } else {
      System.out.println("false");

    }
  }
}