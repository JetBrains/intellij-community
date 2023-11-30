// "Extract if (a)" "true-preview"
class TestThreadInspection {
  void f(boolean a, boolean b, boolean c){
    if ((<caret>a || b)) {
      System.out.println("c");
    }
  }
}