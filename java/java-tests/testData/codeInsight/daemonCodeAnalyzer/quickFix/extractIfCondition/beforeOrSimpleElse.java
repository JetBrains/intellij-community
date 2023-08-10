// "Extract if (a)" "true-preview"
class TestThreadInspection {
  void f(boolean a, boolean b, boolean c){
    if (<caret>a || (b/*the comment inside redundant parenthesis*/)) {
      System.out.println("a&b");
    }
    //simple end comment
    else {
      System.out.println("c");
    }
  }
}