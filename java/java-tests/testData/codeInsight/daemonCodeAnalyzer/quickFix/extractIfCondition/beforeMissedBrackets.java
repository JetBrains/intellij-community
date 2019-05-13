// "Extract if (a)" "true"
class TestThreadInspection {
  void f(boolean a, boolean b, boolean c){
    if (<caret>a && b)
      System.out.println("a&b");//first comment
    else if (c) {
      System.out.println("c");
    }
  }
}