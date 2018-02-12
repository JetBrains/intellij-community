// "Extract if (a)" "true"
class TestThreadInspection {
  void f(boolean a, boolean b, boolean c){
    if (<caret>a || b) {
      System.out.println("a&b");
    }
    //simple end comment
    else {
      System.out.println("c");
    }
  }
}