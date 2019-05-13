// "Extract if (a)" "true"
class Test {
  void f(boolean a, boolean b, boolean c, boolean d){
    if (<caret>a && b)
      System.out.println("a&b");//first comment
    else if (a && c) {
      System.out.println("a&c");//ac
    }
    else if (a) {
      System.out.println("a");//a
    }
    else if (d) {
      System.out.println("d");//d
    }
  }
}