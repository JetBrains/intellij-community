// "Extract if (a)" "true-preview"
class TestThreadInspection {
  void f(boolean a, boolean b, boolean c){
      if (a) {
          System.out.println("c");
      } else if (b) {
          System.out.println("c");
      }
  }
}