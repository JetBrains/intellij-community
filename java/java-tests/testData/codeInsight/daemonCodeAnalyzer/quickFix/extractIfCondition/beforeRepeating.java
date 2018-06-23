// "Extract if (a)" "true"
class Test {
  void f(boolean a, boolean b, boolean c){
    if (<caret>a && b)
      System.out.println("a&b");//first comment
    else if (a && c/*2*/) {
      System.out.println("a&c"); // three
    }
  }
}